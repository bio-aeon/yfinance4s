package org.coinductive.yfinance4s.models

/** Bundled result of the three `Markets` queries for a single region. */
final case class MarketSnapshot(
    region: MarketRegion,
    summary: MarketSummary,
    status: MarketStatus,
    trending: List[TrendingTicker]
) {
  def isOpen: Boolean = status.isOpen
  def isClosed: Boolean = status.isClosed
}
