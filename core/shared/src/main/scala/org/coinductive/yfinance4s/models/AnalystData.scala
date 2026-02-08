package org.coinductive.yfinance4s.models

/** Comprehensive analyst data for a security.
  *
  * @param priceTargets
  *   Analyst consensus price targets and recommendation
  * @param recommendations
  *   Recommendation trends by period (buy/hold/sell counts)
  * @param upgradeDowngradeHistory
  *   Historical analyst upgrades, downgrades, and initiations
  * @param earningsEstimates
  *   Analyst earnings (EPS) estimates by period
  * @param revenueEstimates
  *   Analyst revenue estimates by period
  * @param epsTrends
  *   EPS consensus trend over time by period
  * @param epsRevisions
  *   EPS revision counts by period
  * @param earningsHistory
  *   Historical EPS actual vs. estimate
  * @param growthEstimates
  *   Growth estimates with index comparison
  */
final case class AnalystData(
    priceTargets: Option[AnalystPriceTargets],
    recommendations: List[RecommendationTrend],
    upgradeDowngradeHistory: List[UpgradeDowngrade],
    earningsEstimates: List[EarningsEstimate],
    revenueEstimates: List[RevenueEstimate],
    epsTrends: List[EpsTrend],
    epsRevisions: List[EpsRevisions],
    earningsHistory: List[EarningsHistory],
    growthEstimates: List[GrowthEstimates]
) {

  /** True if any analyst data is available. */
  def nonEmpty: Boolean =
    priceTargets.isDefined ||
      recommendations.nonEmpty ||
      upgradeDowngradeHistory.nonEmpty ||
      earningsEstimates.nonEmpty ||
      revenueEstimates.nonEmpty ||
      earningsHistory.nonEmpty ||
      growthEstimates.nonEmpty

  /** True if no analyst data is available. */
  def isEmpty: Boolean = !nonEmpty

  /** The current month's recommendation trend (period "0m"), if available. */
  def currentRecommendation: Option[RecommendationTrend] =
    recommendations.find(_.period == "0m")

  /** The current quarter's earnings estimate (period "0q"), if available. */
  def currentQuarterEarningsEstimate: Option[EarningsEstimate] =
    earningsEstimates.find(_.period == "0q")

  /** The current quarter's revenue estimate (period "0q"), if available. */
  def currentQuarterRevenueEstimate: Option[RevenueEstimate] =
    revenueEstimates.find(_.period == "0q")

  /** The current year's earnings estimate (period "0y"), if available. */
  def currentYearEarningsEstimate: Option[EarningsEstimate] =
    earningsEstimates.find(_.period == "0y")

  /** The number of consecutive quarters where earnings beat estimates. */
  def consecutiveBeats: Int =
    earningsHistory.sorted.takeWhile(_.isBeat.getOrElse(false)).size

  /** Recent upgrades (last N entries that are upgrades). */
  def recentUpgrades(limit: Int = 5): List[UpgradeDowngrade] =
    upgradeDowngradeHistory.sorted.filter(_.isUpgrade).take(limit)

  /** Recent downgrades (last N entries that are downgrades). */
  def recentDowngrades(limit: Int = 5): List[UpgradeDowngrade] =
    upgradeDowngradeHistory.sorted.filter(_.isDowngrade).take(limit)

  /** Earnings beat rate over available history (percentage of quarters that beat). */
  def earningsBeatRate: Option[Double] = {
    val beats = earningsHistory.flatMap(_.isBeat)
    if (beats.nonEmpty)
      Some(beats.count(identity).toDouble / beats.size * 100.0)
    else None
  }
}

object AnalystData {

  /** Empty analyst data. */
  val empty: AnalystData = AnalystData(
    priceTargets = None,
    recommendations = List.empty,
    upgradeDowngradeHistory = List.empty,
    earningsEstimates = List.empty,
    revenueEstimates = List.empty,
    epsTrends = List.empty,
    epsRevisions = List.empty,
    earningsHistory = List.empty,
    growthEstimates = List.empty
  )
}
