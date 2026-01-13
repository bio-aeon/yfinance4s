package org.coinductive.yfinance4s.models

/** Breakdown of major shareholders by category.
  *
  * @param insidersPercentHeld
  *   Percentage of shares held by company insiders (executives, directors)
  * @param institutionsPercentHeld
  *   Percentage of total outstanding shares held by institutions
  * @param institutionsFloatPercentHeld
  *   Percentage of float (tradeable shares) held by institutions
  * @param institutionsCount
  *   Total number of institutional holders
  */
final case class MajorHolders(
    insidersPercentHeld: Double,
    institutionsPercentHeld: Double,
    institutionsFloatPercentHeld: Double,
    institutionsCount: Int
) {

  /** Percentage held by retail investors (individuals) */
  def retailPercentHeld: Double =
    Math.max(0.0, 1.0 - insidersPercentHeld - institutionsPercentHeld)

  /** Check if the stock is primarily institutionally owned (>50%) */
  def isInstitutionallyDominated: Boolean = institutionsPercentHeld > 0.5

  /** Check if there is significant insider ownership (>5%) */
  def hasSignificantInsiderOwnership: Boolean = insidersPercentHeld > 0.05
}
