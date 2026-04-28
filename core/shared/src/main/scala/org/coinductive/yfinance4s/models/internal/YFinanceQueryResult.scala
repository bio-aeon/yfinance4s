package org.coinductive.yfinance4s.models.internal

import cats.data.NonEmptyList
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.coinductive.yfinance4s.models.internal.YFinanceQueryResult.Chart

private[yfinance4s] final case class YFinanceQueryResult(chart: Chart)

private[yfinance4s] object YFinanceQueryResult {
  implicit val decoder: Decoder[YFinanceQueryResult] = deriveDecoder

  private[yfinance4s] final case class Chart(result: List[InstrumentData], error: Option[YahooErrorBody])

  private[yfinance4s] object Chart {
    implicit val decoder: Decoder[Chart] = Decoder.instance { c =>
      for {
        result <- c.downField("result").as[Option[List[InstrumentData]]].map(_.getOrElse(Nil))
        error <- c.downField("error").as[Option[YahooErrorBody]]
      } yield Chart(result, error)
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
}
