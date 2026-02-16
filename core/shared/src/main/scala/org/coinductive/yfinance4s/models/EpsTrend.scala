package org.coinductive.yfinance4s.models

/** EPS consensus estimate trend over recent time windows.
  *
  * Shows how the consensus EPS estimate has changed over the past 90 days.
  *
  * @param period
  *   The estimate period (e.g., "0q", "+1q", "0y", "+1y")
  * @param current
  *   Current consensus EPS estimate
  * @param sevenDaysAgo
  *   Consensus EPS estimate 7 days ago
  * @param thirtyDaysAgo
  *   Consensus EPS estimate 30 days ago
  * @param sixtyDaysAgo
  *   Consensus EPS estimate 60 days ago
  * @param ninetyDaysAgo
  *   Consensus EPS estimate 90 days ago
  */
final case class EpsTrend(
    period: String,
    current: Option[Double],
    sevenDaysAgo: Option[Double],
    thirtyDaysAgo: Option[Double],
    sixtyDaysAgo: Option[Double],
    ninetyDaysAgo: Option[Double]
) {

  /** The change in consensus EPS over the last 7 days. */
  def changeSevenDays: Option[Double] =
    for {
      c <- current
      s <- sevenDaysAgo
    } yield c - s

  /** The change in consensus EPS over the last 30 days. */
  def changeThirtyDays: Option[Double] =
    for {
      c <- current
      t <- thirtyDaysAgo
    } yield c - t

  /** The change in consensus EPS over the last 90 days. */
  def changeNinetyDays: Option[Double] =
    for {
      c <- current
      n <- ninetyDaysAgo
    } yield c - n

  /** Whether the consensus EPS has been trending upward over 30 days. */
  def isTrendingUp: Option[Boolean] =
    changeThirtyDays.map(_ > 0)
}

object EpsTrend {

  /** Ordering by period: current periods first. */
  implicit val ordering: Ordering[EpsTrend] = Ordering.by(_.period)
}
