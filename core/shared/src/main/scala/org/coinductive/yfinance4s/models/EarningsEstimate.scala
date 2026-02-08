package org.coinductive.yfinance4s.models

/** Analyst earnings (EPS) estimates for a specific period.
  *
  * @param period
  *   The estimate period (e.g., "0q" = current quarter, "+1q" = next quarter, "0y" = current year, "+1y" = next year)
  * @param endDate
  *   The end date of the period (e.g., "2024-06-30"), if available
  * @param avg
  *   Average analyst EPS estimate
  * @param low
  *   Lowest analyst EPS estimate
  * @param high
  *   Highest analyst EPS estimate
  * @param yearAgoEps
  *   EPS from the same period one year ago
  * @param numberOfAnalysts
  *   Number of analysts providing estimates
  * @param growth
  *   Expected EPS growth rate vs. year-ago period
  */
final case class EarningsEstimate(
    period: String,
    endDate: Option[String],
    avg: Option[Double],
    low: Option[Double],
    high: Option[Double],
    yearAgoEps: Option[Double],
    numberOfAnalysts: Option[Int],
    growth: Option[Double]
) {

  /** The range of analyst EPS estimates (high - low). */
  def estimateRange: Option[Double] =
    for {
      h <- high
      l <- low
    } yield h - l

  /** The spread of estimates as a percentage of the average. Lower values indicate higher analyst consensus. */
  def estimateSpreadPercent: Option[Double] =
    for {
      range <- estimateRange
      a <- avg
      if a != 0
    } yield (range / Math.abs(a)) * 100.0

  /** Whether this is a quarterly estimate period ("0q", "+1q", etc.) */
  def isQuarterly: Boolean = period.endsWith("q")

  /** Whether this is a yearly estimate period ("0y", "+1y", etc.) */
  def isYearly: Boolean = period.endsWith("y")
}

object EarningsEstimate {

  /** Ordering by period: current periods first. */
  implicit val ordering: Ordering[EarningsEstimate] = Ordering.by(_.period)
}
