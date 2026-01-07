package org.coinductive.yfinance4s.models

import cats.Show
import enumeratum.*

sealed abstract class ContractSize(val value: String, val multiplier: Int) extends EnumEntry

object ContractSize extends Enum[ContractSize] {
  case object Regular extends ContractSize("REGULAR", 100)
  case object Mini extends ContractSize("MINI", 10)

  val values: IndexedSeq[ContractSize] = findValues

  def fromString(s: String): ContractSize = s.toUpperCase match {
    case "MINI" => Mini
    case _      => Regular
  }

  implicit val show: Show[ContractSize] = Show.show(_.value)
}
