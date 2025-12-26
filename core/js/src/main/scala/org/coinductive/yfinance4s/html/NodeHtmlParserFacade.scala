package org.coinductive.yfinance4s.html

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("node-html-parser", "parse")
object NodeHtmlParserNative extends js.Object {
  def apply(html: String): HTMLElement = js.native
}

@js.native
trait HTMLElement extends js.Object {
  def querySelectorAll(selector: String): js.Array[HTMLElement] = js.native
  def getAttribute(name: String): String = js.native
  def innerHTML: String = js.native
}
