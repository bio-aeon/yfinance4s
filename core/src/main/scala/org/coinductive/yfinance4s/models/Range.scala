package org.coinductive.yfinance4s.models

import cats.Show
import enumeratum.values.{StringEnum, StringEnumEntry}

sealed abstract class Range(val value: String) extends StringEnumEntry

object Range extends StringEnum[Range] {
  case object `1Day` extends Range("1d")
  case object `5Days` extends Range("5d")
  case object `1Month` extends Range("1mo")
  case object `3Months` extends Range("3mo")
  case object `6Months` extends Range("6mo")
  case object `1Year` extends Range("1y")
  case object `2Years` extends Range("2y")
  case object `5Years` extends Range("5y")
  case object `10Years` extends Range("10y")
  case object YearToDate extends Range("ytd")
  case object Max extends Range("max")

  val values = findValues

  implicit val show: Show[Range] = Show.show(_.value)
}
