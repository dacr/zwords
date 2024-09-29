package fr.janalyse.zwords.dictionary

enum DictionaryIssue(message:String) extends Exception(message) {
  case LanguageNotSupported(language: String) extends DictionaryIssue(s"language $language not supported")
  case MissingConfiguration(message: String) extends DictionaryIssue(message)
  case ResourceNotFound(resource: String) extends DictionaryIssue(s"$resource not found")
  case InternalIssue(message: String, throwable: Option[Throwable]=None) extends DictionaryIssue(message)
}
