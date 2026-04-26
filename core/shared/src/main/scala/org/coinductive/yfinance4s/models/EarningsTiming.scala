package org.coinductive.yfinance4s.models

import enumeratum.values.{StringEnum, StringEnumEntry}

/** Scheduled timing modifier for an earnings event (the `startdatetimetype` field on Yahoo's visualization response).
  * Maps Yahoo's plain-text values to typed domain values.
  */
sealed abstract class EarningsTiming(val value: String) extends StringEnumEntry

object EarningsTiming extends StringEnum[EarningsTiming] {
  case object BeforeMarketOpen extends EarningsTiming("BMO")
  case object AfterMarketClose extends EarningsTiming("AMC")
  case object TimeNotSupplied extends EarningsTiming("TNS")
  case object TimeAsSpecified extends EarningsTiming("TAS")

  val values: IndexedSeq[EarningsTiming] = findValues
}
