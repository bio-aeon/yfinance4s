package io.github.coinductive.yfinance4s.models.internal

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
// so a missing `meta` is a malformed response and fails decoding.
private[yfinance4s] final case class InstrumentData(
    meta: ChartMetaRaw,
    timestamp: List[Long],
    indicators: Indicators,
    events: Option[Events]
)

private[yfinance4s] object InstrumentData {

  // For a window without a session, Yahoo sends no `timestamp` and quote objects that hold no columns: an instrument
  // without rows, whose indicators are not read.
  implicit val decoder: Decoder[InstrumentData] = Decoder.instance { c =>
    for {
      meta <- c.downField("meta").as[ChartMetaRaw]
      timestamp <- c.downField("timestamp").as[Option[List[Long]]]
      indicators <- timestamp match {
        case Some(_) => c.downField("indicators").as[Indicators]
        case None    => Right(Indicators.WithoutRows)
      }
      events <- c.downField("events").as[Option[Events]]
    } yield InstrumentData(meta, timestamp.getOrElse(Nil), indicators, events)
  }
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
) {

  /** The regular session Yahoo reports as current, when the latest regular-market print falls inside it. While an
    * exchange is closed, Yahoo reports its last session or already the next one, and the print lies outside either.
    */
  def openRegularSession: Option[TradingPeriodRaw] =
    for {
      session <- currentTradingPeriod.flatMap(_.regular)
      latestPrint <- regularMarketTime
      if session.contains(latestPrint)
    } yield session
}

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
) {

  /** Whether the epoch second falls within the session. The end is excluded: Yahoo stamps the closing print at it. */
  def contains(epochSecond: Long): Boolean = start <= epochSecond && epochSecond < end
}

private[yfinance4s] object TradingPeriodRaw {
  implicit val decoder: Decoder[TradingPeriodRaw] = deriveDecoder
}

// Yahoo sends no `adjclose` for an intraday interval.
private[yfinance4s] final case class Indicators(quote: NonEmptyList[Quote], adjclose: Option[NonEmptyList[AdjClose]])

private[yfinance4s] object Indicators {

  val WithoutRows: Indicators =
    Indicators(NonEmptyList.one(Quote(close = Nil, open = Nil, volume = Nil, high = Nil, low = Nil)), adjclose = None)

  implicit val decoder: Decoder[Indicators] = deriveDecoder
}

private[yfinance4s] final case class Quote(
    close: List[Option[Double]],
    open: List[Option[Double]],
    volume: List[Option[Long]],
    high: List[Option[Double]],
    low: List[Option[Double]]
)

private[yfinance4s] object Quote {
  implicit val decoder: Decoder[Quote] = deriveDecoder
}

private[yfinance4s] final case class AdjClose(adjclose: List[Option[Double]])

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
