package org.coinductive.yfinance4s.models

import enumeratum.*

/** Frequency for financial statement data retrieval.
  *
  * @param apiValue
  *   The value used in the Yahoo Finance API request
  */
sealed abstract class Frequency(val apiValue: String) extends EnumEntry

object Frequency extends Enum[Frequency] {
  val values: IndexedSeq[Frequency] = findValues

  /** Annual financial data (up to 4 years of history) */
  case object Yearly extends Frequency("annual")

  /** Quarterly financial data (up to 5 quarters of history) */
  case object Quarterly extends Frequency("quarterly")

  /** Trailing twelve months (TTM) data - only available for income and cash flow statements */
  case object Trailing extends Frequency("trailing")
}
