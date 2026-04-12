package rj.cocacode.utils

suspend fun escapeXml(s: String): String {
  return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

suspend fun escapeXmlAttr(s: String): String {
  return escapeXml(s).replace("\"", "&quot;").replace("'", "&apos;")
}
