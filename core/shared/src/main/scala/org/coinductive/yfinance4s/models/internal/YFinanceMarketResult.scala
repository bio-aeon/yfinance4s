package org.coinductive.yfinance4s.models.internal

import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, DecodingFailure}
import org.coinductive.yfinance4s.models.internal.MarketDecoders.zonedDateTimeDecoder
import org.coinductive.yfinance4s.models.internal.YFinanceQuoteResult.Value

import java.time.ZonedDateTime
import scala.util.Try

private[internal] object MarketDecoders {
  implicit val zonedDateTimeDecoder: Decoder[ZonedDateTime] =
    Decoder[String].emap(s => Try(ZonedDateTime.parse(s)).toEither.left.map(_.getMessage))
}

// --- Market summary ---

private[yfinance4s] final case class YFinanceMarketSummaryResult(result: List[MarketQuoteRaw])

private[yfinance4s] object YFinanceMarketSummaryResult {
  implicit val decoder: Decoder[YFinanceMarketSummaryResult] = {
    val inner: Decoder[YFinanceMarketSummaryResult] = deriveDecoder
    inner.prepare(_.downField("marketSummaryResponse"))
  }
}

/** Only `symbol` is structurally guaranteed. Other fields are requested via the `fields=` query param and may be absent
  * for halted/newly-listed instruments.
  */
private[yfinance4s] final case class MarketQuoteRaw(
    symbol: String,
    shortName: Option[String],
    fullExchangeName: Option[String],
    exchange: Option[String],
    currency: Option[String],
    marketState: Option[String],
    exchangeTimezoneName: Option[String],
    regularMarketPrice: Option[Value[Double]],
    regularMarketChange: Option[Value[Double]],
    regularMarketChangePercent: Option[Value[Double]],
    regularMarketTime: Option[Value[Long]],
    previousClose: Option[Value[Double]]
)

private[yfinance4s] object MarketQuoteRaw {
  implicit val decoder: Decoder[MarketQuoteRaw] = deriveDecoder
}

// --- Market status (markettime) ---

private[yfinance4s] final case class YFinanceMarketStatusResult(marketTime: MarketTimeRaw)

private[yfinance4s] object YFinanceMarketStatusResult {
  implicit val decoder: Decoder[YFinanceMarketStatusResult] =
    Decoder.instance { c =>
      c.downField("finance")
        .downField("marketTimes")
        .downArray
        .downField("marketTime")
        .downArray
        .as[MarketTimeRaw]
        .map(YFinanceMarketStatusResult(_))
    }
}

/** Core fields (`status`, `open`, `close`, `timezone`) are structurally guaranteed by `/markettime` and therefore
  * required. Auxiliary metadata fields stay `Option`.
  */
private[yfinance4s] final case class MarketTimeRaw(
    status: String,
    open: ZonedDateTime,
    close: ZonedDateTime,
    timezone: TimezoneRaw,
    id: Option[String],
    name: Option[String],
    message: Option[String],
    yfitMarketId: Option[String],
    yfitMarketState: Option[String],
    time: Option[ZonedDateTime]
)

private[yfinance4s] object MarketTimeRaw {
  implicit val decoder: Decoder[MarketTimeRaw] = Decoder.instance { c =>
    for {
      status <- c.downField("status").as[String]
      open <- c.downField("open").as[ZonedDateTime]
      close <- c.downField("close").as[ZonedDateTime]
      timezoneArray <- c.downField("timezone").as[List[TimezoneRaw]]
      timezone <- timezoneArray.headOption.toRight(DecodingFailure("'timezone' array is empty", c.history))
      id <- c.downField("id").as[Option[String]]
      name <- c.downField("name").as[Option[String]]
      message <- c.downField("message").as[Option[String]]
      yfitMarketId <- c.downField("yfitMarketId").as[Option[String]]
      yfitMarketState <- c.downField("yfitMarketState").as[Option[String]]
      time <- c.downField("time").as[Option[ZonedDateTime]]
    } yield MarketTimeRaw(status, open, close, timezone, id, name, message, yfitMarketId, yfitMarketState, time)
  }
}

/** `short` and `gmtoffset` are structurally guaranteed; `full` is optional. */
private[yfinance4s] final case class TimezoneRaw(
    short: String,
    gmtoffset: Long,
    full: Option[String]
)

private[yfinance4s] object TimezoneRaw {
  implicit val decoder: Decoder[TimezoneRaw] = deriveDecoder
}

// --- Trending ---

private[yfinance4s] final case class YFinanceTrendingResult(quotes: List[TrendingQuoteRaw])

private[yfinance4s] object YFinanceTrendingResult {
  implicit val decoder: Decoder[YFinanceTrendingResult] =
    Decoder.instance { c =>
      c.downField("finance")
        .downField("result")
        .downArray
        .downField("quotes")
        .as[List[TrendingQuoteRaw]]
        .map(YFinanceTrendingResult(_))
    }
}

private[yfinance4s] final case class TrendingQuoteRaw(symbol: String)

private[yfinance4s] object TrendingQuoteRaw {
  implicit val decoder: Decoder[TrendingQuoteRaw] = deriveDecoder
}
