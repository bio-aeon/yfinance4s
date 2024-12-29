package org.coinductive.yfinance4s

import cats.Show
import enumeratum.values.{StringEnum, StringEnumEntry}

sealed abstract class Interval(val value: String) extends StringEnumEntry

object Interval extends StringEnum[Interval] {
  case object `1Minute` extends Interval("1m")
  case object `2Minutes` extends Interval("2m")
  case object `5Minutes` extends Interval("5m")
  case object `15Minutes` extends Interval("15m")
  case object `30Minutes` extends Interval("30m")
  case object `60Minutes` extends Interval("60m")
  case object `90Minutes` extends Interval("90m")
  case object `1Hour` extends Interval("1h")
  case object `1Day` extends Interval("1d")
  case object `5Days` extends Interval("5d")
  case object `1Week` extends Interval("1wk")
  case object `1Month` extends Interval("1mo")
  case object `3Months` extends Interval("3mo")

  val values = findValues

  implicit val show: Show[Interval] = Show.show(_.value)
}
