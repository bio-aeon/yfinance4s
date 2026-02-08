package org.coinductive.yfinance4s.models

/** Growth estimates for the stock compared against the index benchmark.
  *
  * @param period
  *   The estimate period (e.g., "0q", "+1q", "0y", "+1y")
  * @param stockGrowth
  *   The estimated earnings growth for the stock
  * @param indexGrowth
  *   The estimated earnings growth for the benchmark index (e.g., S&P 500)
  * @param indexSymbol
  *   The benchmark index symbol (e.g., "SP5" for S&P 500)
  */
final case class GrowthEstimates(
    period: String,
    stockGrowth: Option[Double],
    indexGrowth: Option[Double],
    indexSymbol: Option[String]
) {

  /** Whether the stock is expected to outgrow the index. */
  def isOutperforming: Option[Boolean] =
    for {
      sg <- stockGrowth
      ig <- indexGrowth
    } yield sg > ig

  /** The growth differential (stock growth - index growth). */
  def growthDifferential: Option[Double] =
    for {
      sg <- stockGrowth
      ig <- indexGrowth
    } yield sg - ig
}

object GrowthEstimates {

  /** Ordering by period: current periods first. */
  implicit val ordering: Ordering[GrowthEstimates] = Ordering.by(_.period)
}
