package org.coinductive.yfinance4s.models

import java.time.LocalDate

final case class OptionChain(
    expirationDate: LocalDate,
    calls: List[OptionContract],
    puts: List[OptionContract],
    strikes: List[Double],
    hasMiniOptions: Boolean
) {

  def allContracts: List[OptionContract] = (calls ++ puts).sorted

  def callAtStrike(strike: Double): Option[OptionContract] =
    calls.find(_.strike == strike)

  def putAtStrike(strike: Double): Option[OptionContract] =
    puts.find(_.strike == strike)

  def straddleAtStrike(strike: Double): Option[(OptionContract, OptionContract)] =
    for {
      call <- callAtStrike(strike)
      put <- putAtStrike(strike)
    } yield (call, put)

  def atmStrike(underlyingPrice: Double): Option[Double] =
    strikes.minByOption(s => math.abs(s - underlyingPrice))

  def itmCalls: List[OptionContract] = calls.filter(_.inTheMoney)

  def otmCalls: List[OptionContract] = calls.filterNot(_.inTheMoney)

  def itmPuts: List[OptionContract] = puts.filter(_.inTheMoney)

  def otmPuts: List[OptionContract] = puts.filterNot(_.inTheMoney)

  def totalCallVolume: Long = calls.flatMap(_.volume).sum

  def totalPutVolume: Long = puts.flatMap(_.volume).sum

  def putCallRatio: Option[Double] = {
    val callVol = totalCallVolume
    if (callVol > 0) Some(totalPutVolume.toDouble / callVol) else None
  }

  def totalCallOpenInterest: Long = calls.flatMap(_.openInterest).sum

  def totalPutOpenInterest: Long = puts.flatMap(_.openInterest).sum

  def activeStrikeCount: Int = strikes.size
}

object OptionChain {
  val empty: OptionChain = OptionChain(
    expirationDate = LocalDate.of(1970, 1, 1),
    calls = List.empty,
    puts = List.empty,
    strikes = List.empty,
    hasMiniOptions = false
  )

  implicit val ordering: Ordering[OptionChain] = Ordering.by(_.expirationDate)
}
