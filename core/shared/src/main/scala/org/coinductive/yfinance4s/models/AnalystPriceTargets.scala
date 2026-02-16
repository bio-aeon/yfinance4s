package org.coinductive.yfinance4s.models

/** Analyst consensus price targets for a security.
  *
  * @param currentPrice
  *   The current market price of the security
  * @param targetHigh
  *   The highest analyst price target
  * @param targetLow
  *   The lowest analyst price target
  * @param targetMean
  *   The mean (average) analyst price target
  * @param targetMedian
  *   The median analyst price target
  * @param numberOfAnalysts
  *   The number of analysts providing price targets
  * @param recommendationKey
  *   Consensus recommendation as a string (e.g., "buy", "hold", "sell", "strong_buy", "underperform")
  * @param recommendationMean
  *   Consensus recommendation on a 1-5 scale (1 = Strong Buy, 5 = Strong Sell)
  */
final case class AnalystPriceTargets(
    currentPrice: Double,
    targetHigh: Double,
    targetLow: Double,
    targetMean: Double,
    targetMedian: Double,
    numberOfAnalysts: Int,
    recommendationKey: String,
    recommendationMean: Double
) {

  /** The potential upside from the mean target, as a percentage. Positive values indicate upside, negative values
    * indicate downside.
    */
  def meanUpsidePercent: Double =
    if (currentPrice > 0) ((targetMean - currentPrice) / currentPrice) * 100.0
    else 0.0

  /** The potential upside from the median target, as a percentage. */
  def medianUpsidePercent: Double =
    if (currentPrice > 0) ((targetMedian - currentPrice) / currentPrice) * 100.0
    else 0.0

  /** The range of analyst targets (high - low). */
  def targetRange: Double = targetHigh - targetLow

  /** The spread of analyst targets as a percentage of the mean target. Lower values indicate higher analyst consensus.
    */
  def targetSpreadPercent: Double =
    if (targetMean > 0) (targetRange / targetMean) * 100.0
    else 0.0

  /** Whether the current price is below all analyst targets. */
  def isBelowAllTargets: Boolean = currentPrice < targetLow

  /** Whether the current price is above all analyst targets. */
  def isAboveAllTargets: Boolean = currentPrice > targetHigh
}
