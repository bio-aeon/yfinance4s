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

private[yfinance4s] final case class InstrumentData(
    timestamp: List[Long],
    indicators: Indicators,
    events: Option[Events]
)

private[yfinance4s] object InstrumentData {
  implicit val decoder: Decoder[InstrumentData] = deriveDecoder
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
    date: Long
)

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
