package org.coinductive.yfinance4s.models

import java.time.LocalDate

/** Information about a mutual fund holder.
  *
  * @param organization
  *   Name of the mutual fund
  * @param reportDate
  *   Date of the filing
  * @param percentHeld
  *   Percentage of outstanding shares held (as decimal)
  * @param sharesHeld
  *   Number of shares held
  * @param marketValue
  *   Market value of position in USD
  */
final case class MutualFundHolder(
    organization: String,
    reportDate: LocalDate,
    percentHeld: Double,
    sharesHeld: Long,
    marketValue: Long
) {

  /** Percentage held formatted as percentage (e.g., 9.0 for 9%) */
  def percentHeldFormatted: Double = percentHeld * 100.0

  /** Average cost per share based on market value and shares held */
  def averageCostPerShare: Option[Double] =
    if (sharesHeld > 0) Some(marketValue.toDouble / sharesHeld) else None
}

object MutualFundHolder {

  /** Ordering by percentage held (descending) */
  implicit val orderingByPercent: Ordering[MutualFundHolder] =
    Ordering.by[MutualFundHolder, Double](_.percentHeld).reverse
}
