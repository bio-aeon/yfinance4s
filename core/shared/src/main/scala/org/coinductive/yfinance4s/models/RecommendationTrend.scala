package org.coinductive.yfinance4s.models

/** Analyst recommendation summary for a given period.
  *
  * @param period
  *   The time period (e.g., "0m" for current month, "-1m" for last month)
  * @param strongBuy
  *   Number of analysts with a "Strong Buy" recommendation
  * @param buy
  *   Number of analysts with a "Buy" recommendation
  * @param hold
  *   Number of analysts with a "Hold" recommendation
  * @param sell
  *   Number of analysts with a "Sell" recommendation
  * @param strongSell
  *   Number of analysts with a "Strong Sell" recommendation
  */
final case class RecommendationTrend(
    period: String,
    strongBuy: Int,
    buy: Int,
    hold: Int,
    sell: Int,
    strongSell: Int
) {

  /** Total number of analyst recommendations in this period. */
  def totalAnalysts: Int = strongBuy + buy + hold + sell + strongSell

  /** Total bullish recommendations (strong buy + buy). */
  def totalBullish: Int = strongBuy + buy

  /** Total bearish recommendations (sell + strong sell). */
  def totalBearish: Int = sell + strongSell

  /** Bullish percentage of total recommendations. Returns 0 if no analysts. */
  def bullishPercent: Double =
    if (totalAnalysts > 0) totalBullish.toDouble / totalAnalysts * 100.0
    else 0.0

  /** Bearish percentage of total recommendations. Returns 0 if no analysts. */
  def bearishPercent: Double =
    if (totalAnalysts > 0) totalBearish.toDouble / totalAnalysts * 100.0
    else 0.0

  /** Net sentiment: positive means more bulls, negative means more bears. */
  def netSentiment: Int = totalBullish - totalBearish

  /** Whether the consensus is predominantly bullish (>50% buy/strong buy). */
  def isBullish: Boolean = bullishPercent > 50.0
}

object RecommendationTrend {

  /** Ordering by period: current month first ("0m" < "-1m" < "-2m" < "-3m"). */
  implicit val ordering: Ordering[RecommendationTrend] =
    Ordering.by[RecommendationTrend, Int](parsePeriodMonths).reverse

  private def parsePeriodMonths(r: RecommendationTrend): Int =
    r.period.stripSuffix("m").toIntOption.getOrElse(Int.MinValue)
}
