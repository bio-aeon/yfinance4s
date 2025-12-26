package org.coinductive.yfinance4s.html

object PlatformHtmlParser extends HtmlParser {
  def parse(html: String): HtmlParser.Document = NodeHtmlParser.parse(html)
}
