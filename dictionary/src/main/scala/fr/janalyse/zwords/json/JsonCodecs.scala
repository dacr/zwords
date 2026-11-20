package fr.janalyse.zwords.json

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}

/** jsoniter-scala codecs for every zwords JSON type : the HTTP API, and the records stored in LMDB.
  *
  * The configuration reproduces the wire format zio-json used to produce, so that the switch from zio-json is invisible to the web user
  * interface, and so that the records already stored still decode (and a rollback to a zio-json release can still read new ones) :
  *   - sealed traits as a wrapper object, {"GoodPlaceCell":{"char":"S"}}, rather than jsoniter's default discriminator field
  *   - empty collections and default values written, where jsoniter would omit them ({"rows":[]} is expected by the user interface)
  *   - None values omitted, as zio-json does (jsoniter's default)
  */
object JsonCodecs {
  inline def make[T]: JsonValueCodec[T] =
    JsonCodecMaker.make[T](
      CodecMakerConfig
        .withDiscriminatorFieldName(None)
        .withTransientEmpty(false)
        .withTransientDefault(false)
    )
}
