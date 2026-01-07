package org.coinductive.yfinance4s.models

import java.time.{LocalDate, ZonedDateTime}

final case class OptionContract(
    contractSymbol: String,
    optionType: OptionType,
    strike: Double,
    expiration: LocalDate,
    currency: String,
    lastPrice: Option[Double],
    change: Option[Double],
    percentChange: Option[Double],
    bid: Option[Double],
    ask: Option[Double],
    volume: Option[Long],
    openInterest: Option[Long],
    impliedVolatility: Option[Double],
    inTheMoney: Boolean,
    lastTradeDate: Option[ZonedDateTime],
    contractSize: ContractSize
) {

  def spread: Option[Double] = for {
    b <- bid
    a <- ask
  } yield a - b

  def midPrice: Option[Double] = for {
    b <- bid
    a <- ask
  } yield (a + b) / 2.0

  def spreadPercent: Option[Double] = for {
    s <- spread
    m <- midPrice if m > 0
  } yield (s / m) * 100.0

  def intrinsicValue(underlyingPrice: Double): Double = optionType match {
    case OptionType.Call => math.max(0.0, underlyingPrice - strike)
    case OptionType.Put  => math.max(0.0, strike - underlyingPrice)
  }

  def extrinsicValue(underlyingPrice: Double): Option[Double] = {
    val price = midPrice.orElse(lastPrice)
    price.map(_ - intrinsicValue(underlyingPrice))
  }

  def multiplier: Int = contractSize.multiplier

  def notionalValue: Option[Double] = midPrice.orElse(lastPrice).map(_ * multiplier)
}

object OptionContract {
  implicit val ordering: Ordering[OptionContract] = Ordering.by(c => (c.strike, c.optionType.ordinal))
}
