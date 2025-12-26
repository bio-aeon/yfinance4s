package org.coinductive.yfinance4s.html

trait HtmlParser {
  def parse(html: String): HtmlParser.Document
}

object HtmlParser {

  trait Document {
    def select(cssQuery: String): List[Element]
  }

  trait Element {
    def attr(name: String): String
    def html: String
  }

}
