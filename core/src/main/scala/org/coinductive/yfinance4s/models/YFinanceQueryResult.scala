package org.coinductive.yfinance4s.models

import cats.data.NonEmptyList
import io.circe.generic.JsonCodec
import org.coinductive.yfinance4s.models.YFinanceQueryResult.Chart

@JsonCodec
private[yfinance4s] final case class YFinanceQueryResult(chart: Chart)

private[yfinance4s] object YFinanceQueryResult {

  @JsonCodec
  private[yfinance4s] final case class Chart(result: List[InstrumentData])

  @JsonCodec
  private[yfinance4s] final case class InstrumentData(timestamp: List[Long], indicators: Indicators)

  @JsonCodec
  private[yfinance4s] final case class Indicators(quote: NonEmptyList[Quote], adjclose: NonEmptyList[AdjClose])

  @JsonCodec
  private[yfinance4s] final case class Quote(
      close: List[Double],
      open: List[Double],
      volume: List[Long],
      high: List[Double],
      low: List[Double]
  )

  @JsonCodec
  private[yfinance4s] final case class AdjClose(adjclose: List[Double])
}
