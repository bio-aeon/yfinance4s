package org.coinductive.yfinance4s.models

/** A trending ticker entry returned by Yahoo's `/v1/finance/trending/{region}` endpoint.
  *
  * Currently carries only the symbol, but wraps it in a dedicated type so additional fields (rank, score, delta) can be
  * added without a breaking change.
  */
final case class TrendingTicker(symbol: String) {
  def toTicker: Ticker = Ticker(symbol)
}
