package org.coinductive.yfinance4s.models

import enumeratum.values.{StringEnum, StringEnumEntry}

/** Event kind in a per-ticker earnings feed. Maps Yahoo's numeric string codes (empirically `"1"`, `"2"`, `"11"`) to
  * typed domain values.
  */
sealed abstract class EarningsEventType(val value: String) extends StringEnumEntry

object EarningsEventType extends StringEnum[EarningsEventType] {
  case object EarningsCall extends EarningsEventType("1")
  case object EarningsReport extends EarningsEventType("2")
  case object StockholdersMeeting extends EarningsEventType("11")

  val values: IndexedSeq[EarningsEventType] = findValues
}
