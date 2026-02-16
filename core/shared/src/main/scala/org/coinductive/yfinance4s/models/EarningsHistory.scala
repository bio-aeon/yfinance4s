package org.coinductive.yfinance4s.models

import java.time.LocalDate

/** Historical earnings result for a single quarter.
  *
  * Compares actual reported EPS against analyst estimates for a past quarter.
  *
  * @param quarter
  *   The fiscal quarter end date
  * @param period
  *   The period identifier (e.g., "-4q", "-3q", "-2q", "-1q")
  * @param epsActual
  *   The actual reported EPS
  * @param epsEstimate
  *   The consensus analyst EPS estimate before the earnings report
  * @param epsDifference
  *   The difference between actual and estimate (actual - estimate)
  * @param surprisePercent
  *   The earnings surprise as a percentage of the estimate
  */
final case class EarningsHistory(
    quarter: LocalDate,
    period: String,
    epsActual: Option[Double],
    epsEstimate: Option[Double],
    epsDifference: Option[Double],
    surprisePercent: Option[Double]
) {

  /** Whether earnings beat the analyst estimate. */
  def isBeat: Option[Boolean] =
    epsDifference.map(_ > 0)

  /** Whether earnings missed the analyst estimate. */
  def isMiss: Option[Boolean] =
    epsDifference.map(_ < 0)

  /** Whether earnings exactly met the analyst estimate. */
  def isMet: Option[Boolean] =
    epsDifference.map(_ == 0)
}

object EarningsHistory {

  /** Ordering by quarter date, most recent first. */
  implicit val ordering: Ordering[EarningsHistory] =
    Ordering.by[EarningsHistory, LocalDate](_.quarter).reverse
}
