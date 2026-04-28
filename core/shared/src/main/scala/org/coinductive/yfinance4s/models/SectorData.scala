package org.coinductive.yfinance4s.models

final case class SectorOverview(
    companiesCount: Option[Int] = None,
    marketCap: Option[Long] = None,
    messageBoardId: Option[String] = None,
    description: Option[String] = None,
    industriesCount: Option[Int] = None,
    marketWeight: Option[Double] = None,
    employeeCount: Option[Long] = None
) {

  /** Market weight as a percentage (0–100). */
  def marketWeightPercent: Option[Double] = marketWeight.map(_ * 100)
}

object SectorOverview {
  val empty: SectorOverview = SectorOverview()
}

final case class TopCompany(
    symbol: String,
    name: Option[String],
    rating: Option[String],
    marketWeight: Option[Double]
) {
  def toTicker: Ticker = Ticker(symbol)

  /** Market weight as a percentage (0–100). */
  def marketWeightPercent: Option[Double] = marketWeight.map(_ * 100)
}

final case class SectorETF(symbol: String, name: Option[String]) {
  def toTicker: Ticker = Ticker(symbol)
}

final case class SectorMutualFund(symbol: String, name: Option[String]) {
  def toTicker: Ticker = Ticker(symbol)
}

final case class SectorIndustry(
    key: String,
    name: String,
    symbol: Option[String],
    marketWeight: Option[Double]
) {

  /** Market weight as a percentage (0–100). */
  def marketWeightPercent: Option[Double] = marketWeight.map(_ * 100)
}

object SectorIndustry {
  implicit val ordering: Ordering[SectorIndustry] =
    Ordering.by[SectorIndustry, Option[Double]](_.marketWeight)(using Ordering[Option[Double]].reverse)
}

final case class ResearchReport(
    reportTitle: Option[String] = None,
    provider: Option[String] = None,
    reportDate: Option[String] = None
)

final case class SectorData(
    key: SectorKey,
    name: String,
    symbol: Option[String] = None,
    overview: Option[SectorOverview] = None,
    topCompanies: List[TopCompany] = List.empty,
    topETFs: List[SectorETF] = List.empty,
    topMutualFunds: List[SectorMutualFund] = List.empty,
    industries: List[SectorIndustry] = List.empty,
    researchReports: List[ResearchReport] = List.empty
) {
  def nonEmpty: Boolean =
    overview.isDefined || topCompanies.nonEmpty || topETFs.nonEmpty || industries.nonEmpty

  def isEmpty: Boolean = !nonEmpty

  /** Number of industries in this sector. */
  def industryCount: Int = industries.size

  /** Total market weight of the sector, if available. */
  def totalMarketWeight: Option[Double] = overview.flatMap(_.marketWeight)

  /** Total market weight as a percentage (0–100). */
  def totalMarketWeightPercent: Option[Double] = totalMarketWeight.map(_ * 100)

  /** Total number of companies in the sector, if available. */
  def totalCompanies: Option[Int] = overview.flatMap(_.companiesCount)

  /** Total market capitalization of the sector, if available. */
  def totalMarketCap: Option[Long] = overview.flatMap(_.marketCap)

  /** Sector description text, if available. */
  def description: Option[String] = overview.flatMap(_.description)

  /** Industries sorted by market weight (largest first). */
  def industriesByWeight: List[SectorIndustry] = industries.sorted

  /** Finds an industry within this sector by key. */
  def findIndustry(industryKey: String): Option[SectorIndustry] =
    industries.find(_.key == industryKey)

  /** Top N companies by market weight. */
  def topCompaniesByWeight(n: Int): List[TopCompany] =
    topCompanies
      .sortBy(_.marketWeight)(using Ordering[Option[Double]].reverse)
      .take(n)
}
