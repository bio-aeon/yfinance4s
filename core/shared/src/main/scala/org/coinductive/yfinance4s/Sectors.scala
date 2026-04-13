package org.coinductive.yfinance4s

import cats.Monad
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.internal.*

/** Algebra for sector data (overview, top ETFs/funds, industries). */
trait Sectors[F[_]] {

  /** Retrieves comprehensive data for a sector by its key. */
  def getSectorData(sectorKey: SectorKey): F[Option[SectorData]]

  /** Retrieves just the sector overview (companies count, market cap, market weight, etc.). */
  def getSectorOverview(sectorKey: SectorKey): F[Option[SectorOverview]]

  /** Retrieves top ETFs for a sector. */
  def getTopETFs(sectorKey: SectorKey): F[List[SectorETF]]

  /** Retrieves top mutual funds for a sector. */
  def getTopMutualFunds(sectorKey: SectorKey): F[List[SectorMutualFund]]

  /** Retrieves the list of industries within a sector. */
  def getIndustries(sectorKey: SectorKey): F[List[SectorIndustry]]

  /** Retrieves top companies within a sector. */
  def getTopCompanies(sectorKey: SectorKey): F[List[TopCompany]]
}

private[yfinance4s] object Sectors {

  def apply[F[_]: Monad](gateway: YFinanceGateway[F], auth: YFinanceAuth[F]): Sectors[F] =
    new SectorsImpl(gateway, auth)

  private final class SectorsImpl[F[_]: Monad](gateway: YFinanceGateway[F], auth: YFinanceAuth[F]) extends Sectors[F] {

    private val AllIndustriesSentinel = "All Industries"

    def getSectorData(sectorKey: SectorKey): F[Option[SectorData]] =
      fetchSectorData(sectorKey)(mapToSectorData(sectorKey, _))

    def getSectorOverview(sectorKey: SectorKey): F[Option[SectorOverview]] =
      fetchSectorData(sectorKey)(extractOverview)

    def getTopETFs(sectorKey: SectorKey): F[List[SectorETF]] =
      fetchSectorData(sectorKey)(extractTopETFs)

    def getTopMutualFunds(sectorKey: SectorKey): F[List[SectorMutualFund]] =
      fetchSectorData(sectorKey)(extractTopMutualFunds)

    def getIndustries(sectorKey: SectorKey): F[List[SectorIndustry]] =
      fetchSectorData(sectorKey)(extractIndustries)

    def getTopCompanies(sectorKey: SectorKey): F[List[TopCompany]] =
      fetchSectorData(sectorKey)(extractTopCompanies)

    // --- Private Helpers ---

    private def fetchSectorData[A](sectorKey: SectorKey)(extract: YFinanceSectorResult => A): F[A] =
      auth.getCredentials.flatMap { credentials =>
        gateway.getSectorData(sectorKey, credentials).map(extract)
      }

    private def mapToSectorData(sectorKey: SectorKey, result: YFinanceSectorResult): Option[SectorData] =
      result.data.name.map { name =>
        SectorData(
          key = sectorKey,
          name = name,
          symbol = result.data.symbol,
          overview = extractOverview(result),
          topCompanies = extractTopCompanies(result),
          topETFs = extractTopETFs(result),
          topMutualFunds = extractTopMutualFunds(result),
          industries = extractIndustries(result),
          researchReports = extractResearchReports(result)
        )
      }

    private def extractOverview(result: YFinanceSectorResult): Option[SectorOverview] =
      result.data.overview.map(mapOverview)

    private def extractTopCompanies(result: YFinanceSectorResult): List[TopCompany] =
      result.data.topCompanies.getOrElse(List.empty).flatMap(mapTopCompany)

    private def extractTopETFs(result: YFinanceSectorResult): List[SectorETF] =
      result.data.topETFs.getOrElse(List.empty).flatMap(mapETF)

    private def extractTopMutualFunds(result: YFinanceSectorResult): List[SectorMutualFund] =
      result.data.topMutualFunds.getOrElse(List.empty).flatMap(mapMutualFund)

    private def extractIndustries(result: YFinanceSectorResult): List[SectorIndustry] =
      result.data.industries
        .getOrElse(List.empty)
        .filter(_.name.forall(_ != AllIndustriesSentinel))
        .flatMap(mapIndustry)

    private def extractResearchReports(result: YFinanceSectorResult): List[ResearchReport] =
      result.data.researchReports.getOrElse(List.empty).map(mapResearchReport)

    // --- Mapping helpers ---

    private def mapOverview(raw: SectorOverviewRaw): SectorOverview =
      SectorOverview(
        companiesCount = raw.companiesCount,
        marketCap = raw.marketCap.map(_.raw),
        messageBoardId = raw.messageBoardId,
        description = raw.description,
        industriesCount = raw.industriesCount,
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

    private def mapETF(raw: FundEntryRaw): Option[SectorETF] =
      raw.symbol.map(sym => SectorETF(sym, raw.name))

    private def mapMutualFund(raw: FundEntryRaw): Option[SectorMutualFund] =
      raw.symbol.map(sym => SectorMutualFund(sym, raw.name))

    private def mapIndustry(raw: IndustryEntryRaw): Option[SectorIndustry] =
      for {
        key <- raw.key
        name <- raw.name
      } yield SectorIndustry(
        key = key,
        name = name,
        symbol = raw.symbol,
        marketWeight = raw.marketWeight.map(_.raw)
      )

    private def mapResearchReport(raw: ResearchReportRaw): ResearchReport =
      ResearchReport(
        reportTitle = raw.reportTitle,
        provider = raw.provider,
        reportDate = raw.reportDate
      )
  }
}
