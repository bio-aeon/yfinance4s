package org.coinductive.yfinance4s.models.internal

import cats.data.NonEmptyList
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

private[yfinance4s] final case class Chart(result: List[InstrumentData])

private[yfinance4s] object Chart {

  // result may be null when Yahoo includes an error envelope; treat null/missing as empty list.
  implicit val decoder: Decoder[Chart] = Decoder.instance { c =>
    c.downField("result").as[Option[List[InstrumentData]]].map(_.getOrElse(Nil)).map(Chart(_))
  }
}

// Yahoo ships `meta` on every instrument payload; an error envelope carries no payload at all (result: null),
// so a missing `meta` is a malformed response and fails decoding like a missing `indicators` would.
private[yfinance4s] final case class InstrumentData(
    meta: ChartMetaRaw,
    timestamp: List[Long],
    indicators: Indicators,
    events: Option[Events]
)

private[yfinance4s] object InstrumentData {
  implicit val decoder: Decoder[InstrumentData] = deriveDecoder
}

private[yfinance4s] final case class ChartMetaRaw(
    currency: String,
    symbol: String,
    exchangeName: String,
    fullExchangeName: Option[String],
    instrumentType: String,
    firstTradeDate: Option[Long],
    regularMarketTime: Option[Long],
    gmtoffset: Long,
    timezone: String,
    exchangeTimezoneName: String,
    regularMarketPrice: Option[Double],
    chartPreviousClose: Option[Double],
    priceHint: Option[Int],
    currentTradingPeriod: Option[TradingPeriodsRaw],
    dataGranularity: String,
    range: String,
    validRanges: Option[List[String]],
    hasPrePostMarketData: Option[Boolean]
)

private[yfinance4s] object ChartMetaRaw {
  implicit val decoder: Decoder[ChartMetaRaw] = deriveDecoder
}

private[yfinance4s] final case class TradingPeriodsRaw(
    pre: Option[TradingPeriodRaw],
    regular: Option[TradingPeriodRaw],
    post: Option[TradingPeriodRaw]
)

private[yfinance4s] object TradingPeriodsRaw {
  implicit val decoder: Decoder[TradingPeriodsRaw] = deriveDecoder
}

private[yfinance4s] final case class TradingPeriodRaw(
    timezone: String,
    start: Long,
    end: Long,
    gmtoffset: Long
)

private[yfinance4s] object TradingPeriodRaw {
  implicit val decoder: Decoder[TradingPeriodRaw] = deriveDecoder
}

private[yfinance4s] final case class Indicators(quote: NonEmptyList[Quote], adjclose: NonEmptyList[AdjClose])

private[yfinance4s] object Indicators {
  implicit val decoder: Decoder[Indicators] = deriveDecoder
}

private[yfinance4s] final case class Quote(
    close: List[Double],
    open: List[Double],
    volume: List[Long],
    high: List[Double],
    low: List[Double]
)

private[yfinance4s] object Quote {
  implicit val decoder: Decoder[Quote] = deriveDecoder
}

private[yfinance4s] final case class AdjClose(adjclose: List[Double])

private[yfinance4s] object AdjClose {
  implicit val decoder: Decoder[AdjClose] = deriveDecoder
}

// Event models for dividends and stock splits

private[yfinance4s] final case class Events(
    dividends: Option[Map[String, DividendEventRaw]],
    splits: Option[Map[String, SplitEventRaw]]
)

private[yfinance4s] object Events {
  implicit val decoder: Decoder[Events] = deriveDecoder
}

private[yfinance4s] final case class DividendEventRaw(
    amount: Double,
    date: Long,
    currency: Option[String]
) {

  /** The label with Yahoo's empty/blank sentinel normalised away - the only form the library reads. */
  def normalisedCurrency: Option[String] = currency.map(_.trim).filter(_.nonEmpty)
}

private[yfinance4s] object DividendEventRaw {
  implicit val decoder: Decoder[DividendEventRaw] = deriveDecoder
}

private[yfinance4s] final case class SplitEventRaw(
    date: Long,
    numerator: Int,
    denominator: Int,
    splitRatio: String
)

private[yfinance4s] object SplitEventRaw {
  implicit val decoder: Decoder[SplitEventRaw] = deriveDecoder
}
