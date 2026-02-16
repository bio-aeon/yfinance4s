package org.coinductive.yfinance4s.models

/** Analyst revenue estimates for a specific period.
  *
  * @param period
  *   The estimate period (e.g., "0q", "+1q", "0y", "+1y")
  * @param endDate
  *   The end date of the period, if available
  * @param avg
  *   Average analyst revenue estimate (in currency units)
  * @param low
  *   Lowest analyst revenue estimate
  * @param high
  *   Highest analyst revenue estimate
  * @param numberOfAnalysts
  *   Number of analysts providing estimates
  * @param yearAgoRevenue
  *   Revenue from the same period one year ago
  * @param growth
  *   Expected revenue growth rate vs. year-ago period
  */
final case class RevenueEstimate(
    period: String,
    endDate: Option[String],
    avg: Option[Long],
    low: Option[Long],
    high: Option[Long],
    numberOfAnalysts: Option[Int],
    yearAgoRevenue: Option[Long],
    growth: Option[Double]
) {

  /** The range of analyst revenue estimates (high - low). */
  def estimateRange: Option[Long] =
    for {
      h <- high
      l <- low
    } yield h - l

  /** Whether this is a quarterly estimate period. */
  def isQuarterly: Boolean = period.endsWith("q")

  /** Whether this is a yearly estimate period. */
  def isYearly: Boolean = period.endsWith("y")
}

object RevenueEstimate {

  /** Ordering by period: current periods first. */
  implicit val ordering: Ordering[RevenueEstimate] = Ordering.by(_.period)
}
