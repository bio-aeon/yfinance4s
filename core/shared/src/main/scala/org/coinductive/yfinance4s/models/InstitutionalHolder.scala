package org.coinductive.yfinance4s.models

import java.time.LocalDate

/** Information about an institutional holder (pension fund, asset manager, etc.).
  *
  * @param organization
  *   Name of the institution
  * @param reportDate
  *   Date of the 13F filing
  * @param percentHeld
  *   Percentage of outstanding shares held (as decimal, e.g., 0.09 = 9%)
  * @param sharesHeld
  *   Number of shares held
  * @param marketValue
  *   Market value of position in USD
  */
final case class InstitutionalHolder(
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

object InstitutionalHolder {

  /** Ordering by percentage held (descending) */
  implicit val orderingByPercent: Ordering[InstitutionalHolder] =
    Ordering.by[InstitutionalHolder, Double](_.percentHeld).reverse

  /** Ordering by report date (most recent first) */
  val orderingByDate: Ordering[InstitutionalHolder] =
    Ordering.by[InstitutionalHolder, LocalDate](_.reportDate).reverse
}
