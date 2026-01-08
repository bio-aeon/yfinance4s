package org.coinductive.yfinance4s.models

import cats.Show
import enumeratum.*

sealed abstract class OptionType(val ordinal: Int) extends EnumEntry

object OptionType extends Enum[OptionType] {
  case object Call extends OptionType(0)
  case object Put extends OptionType(1)

  val values: IndexedSeq[OptionType] = findValues

  implicit val show: Show[OptionType] = Show.show {
    case Call => "call"
    case Put  => "put"
  }
}
