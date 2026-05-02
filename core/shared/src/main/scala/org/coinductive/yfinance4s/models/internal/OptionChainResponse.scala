package org.coinductive.yfinance4s.models.internal

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

private[yfinance4s] final case class OptionChainResponse(result: List[OptionDataRaw])

private[yfinance4s] object OptionChainResponse {
  implicit val decoder: Decoder[OptionChainResponse] = deriveDecoder
}

private[yfinance4s] final case class OptionDataRaw(
    underlyingSymbol: String,
    expirationDates: List[Long],
    strikes: List[Double],
    hasMiniOptions: Boolean,
    quote: Option[UnderlyingQuoteRaw],
    options: List[OptionsContainerRaw]
)

private[yfinance4s] object OptionDataRaw {
  implicit val decoder: Decoder[OptionDataRaw] = deriveDecoder
}

private[yfinance4s] final case class UnderlyingQuoteRaw(
    regularMarketPrice: Option[Double],
    bid: Option[Double],
    ask: Option[Double],
    bidSize: Option[Int],
    askSize: Option[Int]
)

private[yfinance4s] object UnderlyingQuoteRaw {
  implicit val decoder: Decoder[UnderlyingQuoteRaw] = deriveDecoder
}

private[yfinance4s] final case class OptionsContainerRaw(
    expirationDate: Long,
    hasMiniOptions: Boolean,
    calls: List[OptionContractRaw],
    puts: List[OptionContractRaw]
)

private[yfinance4s] object OptionsContainerRaw {
  implicit val decoder: Decoder[OptionsContainerRaw] = deriveDecoder
}

private[yfinance4s] final case class OptionContractRaw(
    contractSymbol: String,
    strike: Double,
    currency: String,
    lastPrice: Option[Double],
    change: Option[Double],
    percentChange: Option[Double],
    volume: Option[Long],
    openInterest: Option[Long],
    bid: Option[Double],
    ask: Option[Double],
    contractSize: Option[String],
    expiration: Long,
    lastTradeDate: Option[Long],
    impliedVolatility: Option[Double],
    inTheMoney: Boolean
)

private[yfinance4s] object OptionContractRaw {
  implicit val decoder: Decoder[OptionContractRaw] = deriveDecoder
}
