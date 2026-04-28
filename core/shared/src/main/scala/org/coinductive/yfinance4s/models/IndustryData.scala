package org.coinductive.yfinance4s.models

// --- Overview ---

final case class IndustryOverview(
    companiesCount: Option[Int] = None,
    marketCap: Option[Long] = None,
    messageBoardId: Option[String] = None,
    description: Option[String] = None,
    marketWeight: Option[Double] = None,
    employeeCount: Option[Long] = None
) {

  /** Market weight as a percentage (0-100). */
  def marketWeightPercent: Option[Double] = marketWeight.map(_ * 100)
}

object IndustryOverview {
  val empty: IndustryOverview = IndustryOverview()
}

// --- Parent sector info ---

final case class IndustrySectorInfo(sectorKey: String, sectorName: String) {
  def toSectorKey: SectorKey = SectorKey(sectorKey)
}

// --- Top performing companies ---

final case class TopPerformingCompany(
    symbol: String,
    name: Option[String],
    ytdReturn: Option[Double],
    lastPrice: Option[Double],
    targetPrice: Option[Double]
) {
  def toTicker: Ticker = Ticker(symbol)

  /** YTD return as a percentage (0-100). */
  def ytdReturnPercent: Option[Double] = ytdReturn.map(_ * 100)

  /** Implied upside from last price to analyst target, as a fraction. None when last price is missing, zero, or
    * negative, or when target price is missing.
    */
  def impliedUpside: Option[Double] =
    for {
      last <- lastPrice if last > 0
      target <- targetPrice
    } yield (target - last) / last

  /** Implied upside as a percentage (0-100). */
  def impliedUpsidePercent: Option[Double] = impliedUpside.map(_ * 100)
}

object TopPerformingCompany {
  implicit val ordering: Ordering[TopPerformingCompany] =
    Ordering.by[TopPerformingCompany, Option[Double]](_.ytdReturn)(using Ordering[Option[Double]].reverse)
}

// --- Top growth companies ---

final case class TopGrowthCompany(
    symbol: String,
    name: Option[String],
    ytdReturn: Option[Double],
    growthEstimate: Option[Double]
) {
  def toTicker: Ticker = Ticker(symbol)

  /** YTD return as a percentage (0-100). */
  def ytdReturnPercent: Option[Double] = ytdReturn.map(_ * 100)

  /** Growth estimate as a percentage (0-100). */
  def growthEstimatePercent: Option[Double] = growthEstimate.map(_ * 100)
}

object TopGrowthCompany {
  implicit val ordering: Ordering[TopGrowthCompany] =
    Ordering.by[TopGrowthCompany, Option[Double]](_.growthEstimate)(using Ordering[Option[Double]].reverse)
}

// --- Top-level aggregate ---

final case class IndustryData(
    key: IndustryKey,
    name: String,
    sectorKey: String,
    sectorName: String,
    symbol: Option[String] = None,
    overview: Option[IndustryOverview] = None,
    topCompanies: List[TopCompany] = List.empty,
    topPerformingCompanies: List[TopPerformingCompany] = List.empty,
    topGrowthCompanies: List[TopGrowthCompany] = List.empty,
    researchReports: List[ResearchReport] = List.empty
) {

  def nonEmpty: Boolean =
    overview.isDefined ||
      topCompanies.nonEmpty ||
      topPerformingCompanies.nonEmpty ||
      topGrowthCompanies.nonEmpty

  def isEmpty: Boolean = !nonEmpty

  /** The parent sector of this industry. */
  def sectorInfo: IndustrySectorInfo = IndustrySectorInfo(sectorKey, sectorName)

  /** Total market weight of the industry (fraction), if available. */
  def totalMarketWeight: Option[Double] = overview.flatMap(_.marketWeight)

  /** Total market weight as a percentage (0-100). */
  def totalMarketWeightPercent: Option[Double] = totalMarketWeight.map(_ * 100)

  /** Total number of companies in the industry, if available. */
  def totalCompanies: Option[Int] = overview.flatMap(_.companiesCount)

  /** Total market capitalization of the industry, if available. */
  def totalMarketCap: Option[Long] = overview.flatMap(_.marketCap)

  /** Industry description text, if available. */
  def description: Option[String] = overview.flatMap(_.description)

  /** Top N companies by market weight (descending). */
  def topCompaniesByWeight(n: Int): List[TopCompany] =
    topCompanies.sortBy(_.marketWeight)(using Ordering[Option[Double]].reverse).take(n)

  /** Top N performers by YTD return (descending). */
  def topPerformersByYtd(n: Int): List[TopPerformingCompany] =
    topPerformingCompanies.sorted.take(n)

  /** Top N growth companies by growth estimate (descending). */
  def topGrowthByEstimate(n: Int): List[TopGrowthCompany] =
    topGrowthCompanies.sorted.take(n)

  /** Finds a top-company entry by symbol. */
  def findTopCompany(symbol: String): Option[TopCompany] =
    topCompanies.find(_.symbol == symbol)
}
