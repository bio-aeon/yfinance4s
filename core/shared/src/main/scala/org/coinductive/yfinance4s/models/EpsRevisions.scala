package org.coinductive.yfinance4s.models

/** EPS estimate revision counts for a specific period.
  *
  * Tracks how many analysts have revised their estimates up or down.
  *
  * @param period
  *   The estimate period (e.g., "0q", "+1q", "0y", "+1y")
  * @param upLast7Days
  *   Number of upward revisions in the last 7 days
  * @param upLast30Days
  *   Number of upward revisions in the last 30 days
  * @param downLast30Days
  *   Number of downward revisions in the last 30 days
  * @param downLast90Days
  *   Number of downward revisions in the last 90 days
  */
final case class EpsRevisions(
    period: String,
    upLast7Days: Option[Int],
    upLast30Days: Option[Int],
    downLast30Days: Option[Int],
    downLast90Days: Option[Int]
) {

  /** Net revisions in the last 30 days (positive = more ups than downs). */
  def netRevisions30Days: Option[Int] =
    for {
      up <- upLast30Days
      down <- downLast30Days
    } yield up - down

  /** Whether the revision trend is positive (more ups than downs in last 30 days). */
  def isPositiveTrend: Option[Boolean] =
    netRevisions30Days.map(_ > 0)
}

object EpsRevisions {

  /** Ordering by period: current periods first. */
  implicit val ordering: Ordering[EpsRevisions] = Ordering.by(_.period)
}
