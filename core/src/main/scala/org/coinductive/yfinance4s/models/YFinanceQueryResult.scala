package org.coinductive.yfinance4s.models

import io.circe.generic.JsonCodec
import org.coinductive.yfinance4s.models.YFinanceQueryResult.Chart

@JsonCodec
final case class YFinanceQueryResult(chart: Chart)

object YFinanceQueryResult {

  @JsonCodec
  final case class Chart(result: List[InstrumentData])

  @JsonCodec
  final case class InstrumentData(timestamp: List[Long], indicators: Indicators)

  @JsonCodec
  final case class Indicators(quote: List[Quote], adjclose: List[AdjClose])

  @JsonCodec
  final case class Quote(
      close: List[Double],
      open: List[Double],
      volume: List[Long],
      high: List[Double],
      low: List[Double]
  )

  @JsonCodec
  final case class AdjClose(adjclose: List[Double])
}
