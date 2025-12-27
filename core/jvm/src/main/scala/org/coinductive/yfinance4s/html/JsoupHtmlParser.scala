package org.coinductive.yfinance4s.html

import org.jsoup.Jsoup

import scala.jdk.CollectionConverters.*

object JsoupHtmlParser extends HtmlParser {

  def parse(html: String): HtmlParser.Document = {
    val doc = Jsoup.parse(html)
    new JsoupDocument(doc)
  }

  private final class JsoupDocument(underlying: org.jsoup.nodes.Document) extends HtmlParser.Document {
    def select(cssQuery: String): List[HtmlParser.Element] =
      underlying.select(cssQuery).asScala.toList.map(new JsoupElement(_))
  }

  private final class JsoupElement(underlying: org.jsoup.nodes.Element) extends HtmlParser.Element {
    def attr(name: String): String = underlying.attr(name)
    def html: String = underlying.html
  }

}
