package org.coinductive.yfinance4s.models

import org.coinductive.yfinance4s.models.ChartResult.Quote

import java.time.ZonedDateTime

final case class ChartResult(quotes: List[Quote])

object ChartResult {

  final case class Quote(
      datetime: ZonedDateTime,
      close: Double,
      open: Double,
      volume: Long,
      high: Double,
      low: Double,
      adjclose: Double
  )
}
