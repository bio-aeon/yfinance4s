package org.coinductive.yfinance4s

import cats.Monad
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.internal.*

/** Algebra for industry data (overview, top companies, top performers, top growth). */
trait Industries[F[_]] {

  /** Comprehensive industry data. Raises in F if the key is unknown. */
  def getIndustryData(industryKey: IndustryKey): F[IndustryData]

  /** Just the industry overview (companies count, market cap, market weight, etc.). */
  def getIndustryOverview(industryKey: IndustryKey): F[Option[IndustryOverview]]

  /** The parent sector (key + name) of the industry. */
  def getSectorInfo(industryKey: IndustryKey): F[IndustrySectorInfo]

  /** Top companies within this industry, ordered by market weight. */
  def getTopCompanies(industryKey: IndustryKey): F[List[TopCompany]]

  /** Top performing companies in this industry, sorted by YTD return descending. */
  def getTopPerformingCompanies(industryKey: IndustryKey): F[List[TopPerformingCompany]]

  /** Top growth companies in this industry, sorted by growth estimate descending. */
  def getTopGrowthCompanies(industryKey: IndustryKey): F[List[TopGrowthCompany]]

  /** Research reports for this industry. */
  def getResearchReports(industryKey: IndustryKey): F[List[ResearchReport]]
}

private[yfinance4s] object Industries {

  def apply[F[_]: Monad](gateway: YFinanceGateway[F], auth: YFinanceAuth[F]): Industries[F] =
    new IndustriesImpl(gateway, auth)

  private final class IndustriesImpl[F[_]: Monad](gateway: YFinanceGateway[F], auth: YFinanceAuth[F])
      extends Industries[F] {

    def getIndustryData(industryKey: IndustryKey): F[IndustryData] =
      fetchIndustryData(industryKey)(mapToIndustryData(industryKey, _))

    def getIndustryOverview(industryKey: IndustryKey): F[Option[IndustryOverview]] =
      fetchIndustryData(industryKey)(extractOverview)

    def getSectorInfo(industryKey: IndustryKey): F[IndustrySectorInfo] =
      fetchIndustryData(industryKey)(extractSectorInfo)

    def getTopCompanies(industryKey: IndustryKey): F[List[TopCompany]] =
      fetchIndustryData(industryKey)(extractTopCompanies)

    def getTopPerformingCompanies(industryKey: IndustryKey): F[List[TopPerformingCompany]] =
      fetchIndustryData(industryKey)(extractTopPerformingCompanies)

    def getTopGrowthCompanies(industryKey: IndustryKey): F[List[TopGrowthCompany]] =
      fetchIndustryData(industryKey)(extractTopGrowthCompanies)

    def getResearchReports(industryKey: IndustryKey): F[List[ResearchReport]] =
      fetchIndustryData(industryKey)(extractResearchReports)

    // --- Private helpers ---

    private def fetchIndustryData[A](industryKey: IndustryKey)(
        extract: YFinanceIndustryResult => A
    ): F[A] =
      auth.getCredentials.flatMap { credentials =>
        gateway.getIndustryData(industryKey, credentials).map(extract)
      }

    private def mapToIndustryData(industryKey: IndustryKey, result: YFinanceIndustryResult): IndustryData =
      IndustryData(
        key = industryKey,
        name = result.name,
        sectorKey = result.sectorKey,
        sectorName = result.sectorName,
        symbol = result.symbol,
        overview = extractOverview(result),
        topCompanies = extractTopCompanies(result),
        topPerformingCompanies = extractTopPerformingCompanies(result),
        topGrowthCompanies = extractTopGrowthCompanies(result),
        researchReports = extractResearchReports(result)
      )

    private def extractOverview(result: YFinanceIndustryResult): Option[IndustryOverview] =
      result.overview.map(mapOverview)

    private def extractSectorInfo(result: YFinanceIndustryResult): IndustrySectorInfo =
      IndustrySectorInfo(result.sectorKey, result.sectorName)

    private def extractTopCompanies(result: YFinanceIndustryResult): List[TopCompany] =
      result.topCompanies.getOrElse(List.empty).flatMap(mapTopCompany)

    private def extractTopPerformingCompanies(result: YFinanceIndustryResult): List[TopPerformingCompany] =
      result.topPerformingCompanies.getOrElse(List.empty).map(mapTopPerformingCompany).sorted

    private def extractTopGrowthCompanies(result: YFinanceIndustryResult): List[TopGrowthCompany] =
      result.topGrowthCompanies.getOrElse(List.empty).map(mapTopGrowthCompany).sorted

    private def extractResearchReports(result: YFinanceIndustryResult): List[ResearchReport] =
      result.researchReports.getOrElse(List.empty).map(mapResearchReport)

    // --- Mapping helpers ---

    // industriesCount from raw overview is intentionally dropped; not meaningful for a leaf industry.
    private def mapOverview(raw: SectorOverviewRaw): IndustryOverview =
      IndustryOverview(
        companiesCount = raw.companiesCount,
        marketCap = raw.marketCap.map(_.raw),
        messageBoardId = raw.messageBoardId,
        description = raw.description,
        marketWeight = raw.marketWeight.map(_.raw),
        employeeCount = raw.employeeCount.map(_.raw)
      )

    private def mapTopCompany(raw: TopCompanyRaw): Option[TopCompany] =
      raw.symbol.map { sym =>
        TopCompany(
          symbol = sym,
          name = raw.name,
          rating = raw.rating,
          marketWeight = raw.marketWeight.map(_.raw)
        )
      }

    private def mapTopPerformingCompany(raw: TopPerformingCompanyRaw): TopPerformingCompany =
      TopPerformingCompany(
        symbol = raw.symbol,
        name = raw.name,
        ytdReturn = raw.ytdReturn.map(_.raw),
        lastPrice = raw.lastPrice.map(_.raw),
        targetPrice = raw.targetPrice.map(_.raw)
      )

    private def mapTopGrowthCompany(raw: TopGrowthCompanyRaw): TopGrowthCompany =
      TopGrowthCompany(
        symbol = raw.symbol,
        name = raw.name,
        ytdReturn = raw.ytdReturn.map(_.raw),
        growthEstimate = raw.growthEstimate.map(_.raw)
      )

    private def mapResearchReport(raw: ResearchReportRaw): ResearchReport =
      ResearchReport(
        reportTitle = raw.reportTitle,
        provider = raw.provider,
        reportDate = raw.reportDate
      )
  }
}
