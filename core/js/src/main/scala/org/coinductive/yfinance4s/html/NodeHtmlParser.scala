package org.coinductive.yfinance4s.html

import scala.scalajs.js

object NodeHtmlParser extends HtmlParser {

  def parse(html: String): HtmlParser.Document = {
    val root = NodeHtmlParserNative(html)
    new NodeHtmlDocument(root)
  }

  private final class NodeHtmlDocument(underlying: HTMLElement) extends HtmlParser.Document {
    def select(cssQuery: String): List[HtmlParser.Element] =
      underlying.querySelectorAll(cssQuery).toList.map(new NodeHtmlElement(_))
  }

  private final class NodeHtmlElement(underlying: HTMLElement) extends HtmlParser.Element {
    def attr(name: String): String = {
      val value = underlying.getAttribute(name)
      if (value == null || js.isUndefined(value)) "" else value
    }
    def html: String = {
      val value = underlying.innerHTML
      if (value == null || js.isUndefined(value)) "" else value
    }
  }

}
