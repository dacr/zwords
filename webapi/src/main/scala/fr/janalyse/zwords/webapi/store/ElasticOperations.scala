/*
 * Copyright 2022 David Crosson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.janalyse.zwords.webapi.store

import zio.*
import zio.json.*
import zio.stream.*

import java.time.OffsetDateTime

import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties}
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.ElasticDsl.*
import com.sksamuel.elastic4s.requests.mappings.*
import com.sksamuel.elastic4s.Response
import com.sksamuel.elastic4s.requests.bulk.BulkResponse
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import org.elasticsearch.client.RestClientBuilder.{HttpClientConfigCallback, RequestConfigCallback}
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import scala.concurrent.duration.FiniteDuration
import java.time.temporal.ChronoField
import java.util.concurrent.TimeUnit
import scala.util.Properties.{envOrNone, envOrElse}

case class ElasticOperations(elasticUrl: String, elasticUsername: Option[String], elasticPassword: Option[String]) {

  val client = {
    if (elasticPassword.isEmpty || elasticUsername.isEmpty) ElasticClient(JavaClient(ElasticProperties(elasticUrl)))
    else {
      lazy val provider = {
        val basicProvider = new BasicCredentialsProvider
        val credentials   = new UsernamePasswordCredentials(elasticUsername.get, elasticPassword.get)
        basicProvider.setCredentials(AuthScope.ANY, credentials)
        basicProvider
      }

      val customElasticClient = ElasticClient(
        JavaClient(
          ElasticProperties(elasticUrl),
          (requestConfigBuilder: RequestConfig.Builder) => requestConfigBuilder,
          (httpClientBuilder: HttpAsyncClientBuilder) => httpClientBuilder.setDefaultCredentialsProvider(provider)
        )
      )
      customElasticClient
    }
  }

  val scrollKeepAlive = FiniteDuration(30, "seconds")
  val timeout         = 20.seconds
  val upsertGrouping  = 50
  val searchPageSize  = 500
  //val retrySchedule   = Schedule.exponential(100.millis, 2).jittered && Schedule.recurs(5)

  // ------------------------------------------------------

  def indexNameFromTimestamp(indexPrefix: String, mayBeTimestamp: Option[OffsetDateTime]): String = {
    mayBeTimestamp match {
      case None            => indexPrefix
      case Some(timestamp) =>
        val year  = timestamp.get(ChronoField.YEAR)
        val month = timestamp.get(ChronoField.MONTH_OF_YEAR)
        val week  = timestamp.get(ChronoField.ALIGNED_WEEK_OF_YEAR)
        s"$indexPrefix-$year-$month"
    }
  }

  // ------------------------------------------------------
  def streamFromScroll(scrollId: String) = {
    ZStream.paginateChunkZIO(scrollId) { currentScrollId =>
      val responseFuture = client.execute(searchScroll(currentScrollId).keepAlive(scrollKeepAlive))
      for {
        response    <- ZIO.fromFuture(implicit ec => responseFuture)
        nextScrollId = response.result.scrollId
        results      = Chunk.fromArray(response.result.hits.hits.map(_.sourceAsString))
        // _           <- ZIO.log(s"Got ${results.size} more documents")
      } yield results -> (if (results.size > 0) nextScrollId else None)
    }
  }

  def fetchAll[T](indexName: String)(using JsonDecoder[T]) = {
    def responseFuture = client.execute(search(Index(indexName)).size(searchPageSize).scroll(scrollKeepAlive))

    val result = for {
      response         <- ZIO.fromFuture(implicit ec => responseFuture)
      scrollId         <- ZIO.fromOption(response.result.scrollId)
      firstResults      = Chunk.fromArray(response.result.hits.hits.map(_.sourceAsString))
      // _                <- ZIO.log(s"Got ${firstResults.size} first documents")
      nextResultsStream = streamFromScroll(scrollId)
    } yield ZStream.fromChunk(firstResults) ++ nextResultsStream

    ZStream.unwrap(result).map(_.fromJson[T]).absolve.mapError(err => Exception(err.toString))
  }

  // ------------------------------------------------------
  def fetch[T](
    id: String,
    indexPrefix: String,
    timestamp: Option[OffsetDateTime]
  )(using JsonDecoder[T]) = {
    val indexName = indexNameFromTimestamp(indexPrefix, timestamp)
    val getEffect = for {
      response <- ZIO.fromFuture(implicit ec => client.execute { get(indexName, id) })
      result        <- ZIO.cond(response.isSuccess, response.result.sourceAsString.fromJson[T], response.error.asException)
    } yield result
    getEffect
  }

  // ------------------------------------------------------
  def upsert[T](
    indexPrefix: String,
    document: T,
    timestampExtractor: T => Option[OffsetDateTime],
    idExtractor: T => String
  )(using JsonEncoder[T]) = {
    val indexName = indexNameFromTimestamp(indexPrefix, timestampExtractor(document))
    val id        = idExtractor(document)

    val upsertEffect = for {
      response <- ZIO.fromFuture(implicit ec => client.execute { indexInto(indexName).id(id).doc(document.toJson) })
      _        <- ZIO.cond(response.isSuccess, (), response.error.asException)
    } yield ()
    upsertEffect
  }

  // ------------------------------------------------------
  def upsertChunk[T](
    indexPrefix: String,
    documents: Chunk[T],
    timestampExtractor: T => Option[OffsetDateTime],
    idExtractor: T => String
  )(using JsonEncoder[T]) = {
    def responseFuture = client.execute {
      bulk {
        for { document <- documents } yield {
          val indexName = indexNameFromTimestamp(indexPrefix, timestampExtractor(document))
          val id        = idExtractor(document)
          indexInto(indexName).id(id).doc(document.toJson)
        }
      }
    }
    val upsertEffect   = for {
      response <- ZIO.fromFuture(implicit ec => responseFuture)
      failures  = response.result.failures.flatMap(_.error).map(_.toString)
      _        <- ZIO.log(s"${if (response.isSuccess) "Upserted" else "Failed to upsert"} ${documents.size} into elasticsearch")
      _        <- ZIO.cond(response.isSuccess, (), failures.mkString("\n"))
    } yield ()
    upsertEffect
  }

}
