package org.coinductive.yfinance4s.models.internal

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

/** Raw Yahoo Finance screener API response.
  *
  * Wraps the `finance.result[0]` object which contains the total count and list of quote objects.
  */
private[yfinance4s] final case class YFinanceScreenerResult(
    total: Int,
    quotes: List[ScreenerQuoteRaw]
)

private[yfinance4s] object YFinanceScreenerResult {

  /** Decoder that unwraps the `finance.result[0]` envelope. */
  implicit val decoder: Decoder[YFinanceScreenerResult] = Decoder.instance { c =>
    val result = c.downField("finance").downField("result").downArray
    for {
      total <- result.downField("total").as[Option[Int]]
      quotes <- result.downField("quotes").as[Option[List[ScreenerQuoteRaw]]]
    } yield YFinanceScreenerResult(
      total = total.getOrElse(0),
      quotes = quotes.getOrElse(List.empty)
    )
  }
}

/** Raw quote data from screener response. Field names match Yahoo's JSON exactly. */
private[yfinance4s] final case class ScreenerQuoteRaw(
    symbol: Option[String],
    shortName: Option[String],
    longName: Option[String],
    quoteType: Option[String],
    exchange: Option[String],
    exchangeDisp: Option[String],
    sector: Option[String],
    sectorDisp: Option[String],
    industry: Option[String],
    industryDisp: Option[String],
    regularMarketPrice: Option[Double],
    regularMarketChange: Option[Double],
    regularMarketChangePercent: Option[Double],
    regularMarketVolume: Option[Long],
    marketCap: Option[Long],
    trailingPE: Option[Double],
    forwardPE: Option[Double],
    priceToBook: Option[Double],
    fiftyTwoWeekHigh: Option[Double],
    fiftyTwoWeekLow: Option[Double],
    dividendYield: Option[Double],
    epsTrailingTwelveMonths: Option[Double],
    averageDailyVolume3Month: Option[Long]
)

private[yfinance4s] object ScreenerQuoteRaw {
  implicit val decoder: Decoder[ScreenerQuoteRaw] = deriveDecoder
}
