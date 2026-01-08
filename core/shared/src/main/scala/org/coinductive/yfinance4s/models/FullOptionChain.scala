package org.coinductive.yfinance4s.models

import java.time.LocalDate

final case class FullOptionChain(
    underlyingSymbol: String,
    underlyingPrice: Option[Double],
    expirationDates: List[LocalDate],
    chains: Map[LocalDate, OptionChain]
) {

  def nearestExpiration: Option[LocalDate] =
    expirationDates.sorted.headOption

  def nearestChain: Option[OptionChain] =
    nearestExpiration.flatMap(chains.get)

  def chainsWithinDays(days: Int): List[OptionChain] = {
    val cutoff = LocalDate.now().plusDays(days)
    expirationDates
      .filter(_.isBefore(cutoff))
      .flatMap(chains.get)
      .sorted
  }

  def chainFor(date: LocalDate): Option[OptionChain] = chains.get(date)

  def allChainsSorted: List[OptionChain] =
    expirationDates.sorted.flatMap(chains.get)

  def expirationCount: Int = expirationDates.size

  def daysToNearestExpiration: Option[Long] =
    nearestExpiration.map { exp =>
      java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), exp)
    }
}

object FullOptionChain {
  def apply(
      underlyingSymbol: String,
      underlyingPrice: Option[Double],
      expirationDates: List[LocalDate],
      chain: OptionChain
  ): FullOptionChain = FullOptionChain(
    underlyingSymbol = underlyingSymbol,
    underlyingPrice = underlyingPrice,
    expirationDates = expirationDates,
    chains = Map(chain.expirationDate -> chain)
  )
}
