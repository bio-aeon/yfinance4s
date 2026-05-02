package org.coinductive.yfinance4s

import cats.Monad
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import org.coinductive.yfinance4s.Mapping.*
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.internal.*

/** Algebra for holders and insider data. */
trait Holders[F[_]] {

  /** Retrieves major holders breakdown for a ticker. */
  def getMajorHolders(ticker: Ticker): F[Option[MajorHolders]]

  /** Retrieves top institutional holders for a ticker. */
  def getInstitutionalHolders(ticker: Ticker): F[List[InstitutionalHolder]]

  /** Retrieves top mutual fund holders for a ticker. */
  def getMutualFundHolders(ticker: Ticker): F[List[MutualFundHolder]]

  /** Retrieves recent insider transactions for a ticker. */
  def getInsiderTransactions(ticker: Ticker): F[List[InsiderTransaction]]

  /** Retrieves insider roster (current positions) for a ticker. */
  def getInsiderRoster(ticker: Ticker): F[List[InsiderRosterEntry]]

  /** Retrieves comprehensive holders data for a ticker. */
  def getHoldersData(ticker: Ticker): F[Option[HoldersData]]
}

private[yfinance4s] object Holders {

  def apply[F[_]: Monad](gateway: YFinanceGateway[F], auth: YFinanceAuth[F]): Holders[F] =
    new HoldersImpl(gateway, auth)

  private final class HoldersImpl[F[_]: Monad](
      gateway: YFinanceGateway[F],
      auth: YFinanceAuth[F]
  ) extends Holders[F] {

    def getMajorHolders(ticker: Ticker): F[Option[MajorHolders]] =
      fetchHolders(ticker).map(extractMajorHolders)

    def getInstitutionalHolders(ticker: Ticker): F[List[InstitutionalHolder]] =
      fetchHolders(ticker).map(extractInstitutionalHolders)

    def getMutualFundHolders(ticker: Ticker): F[List[MutualFundHolder]] =
      fetchHolders(ticker).map(extractMutualFundHolders)

    def getInsiderTransactions(ticker: Ticker): F[List[InsiderTransaction]] =
      fetchHolders(ticker).map(extractInsiderTransactions)

    def getInsiderRoster(ticker: Ticker): F[List[InsiderRosterEntry]] =
      fetchHolders(ticker).map(extractInsiderRoster)

    def getHoldersData(ticker: Ticker): F[Option[HoldersData]] =
      fetchHolders(ticker).map(mapToHoldersData)

    // --- Private Fetch + Mapping Helpers ---

    private def fetchHolders(ticker: Ticker): F[HoldersQuoteSummary] =
      auth.getCredentials.flatMap(creds => gateway.getHolders(ticker, creds))

    private def extractMajorHolders(result: HoldersQuoteSummary): Option[MajorHolders] =
      result.result.headOption.flatMap { data =>
        data.majorHoldersBreakdown.flatMap(mapMajorHolders)
      }

    private def extractInstitutionalHolders(result: HoldersQuoteSummary): List[InstitutionalHolder] =
      result.result.headOption
        .flatMap(_.institutionOwnership)
        .flatMap(_.ownershipList)
        .getOrElse(List.empty)
        .flatMap(mapInstitutionalHolder)
        .sorted

    private def extractMutualFundHolders(result: HoldersQuoteSummary): List[MutualFundHolder] =
      result.result.headOption
        .flatMap(_.fundOwnership)
        .flatMap(_.ownershipList)
        .getOrElse(List.empty)
        .flatMap(mapMutualFundHolder)
        .sorted

    private def extractInsiderTransactions(result: HoldersQuoteSummary): List[InsiderTransaction] =
      result.result.headOption
        .flatMap(_.insiderTransactions)
        .flatMap(_.transactions)
        .getOrElse(List.empty)
        .flatMap(mapInsiderTransaction)
        .sorted

    private def extractInsiderRoster(result: HoldersQuoteSummary): List[InsiderRosterEntry] =
      result.result.headOption
        .flatMap(_.insiderHolders)
        .flatMap(_.holders)
        .getOrElse(List.empty)
        .flatMap(mapInsiderRosterEntry)
        .sorted

    private def mapToHoldersData(result: HoldersQuoteSummary): Option[HoldersData] =
      result.result.headOption.map { data =>
        HoldersData(
          majorHolders = data.majorHoldersBreakdown.flatMap(mapMajorHolders),
          institutionalHolders = data.institutionOwnership
            .flatMap(_.ownershipList)
            .getOrElse(List.empty)
            .flatMap(mapInstitutionalHolder)
            .sorted,
          mutualFundHolders = data.fundOwnership
            .flatMap(_.ownershipList)
            .getOrElse(List.empty)
            .flatMap(mapMutualFundHolder)
            .sorted,
          insiderTransactions = data.insiderTransactions
            .flatMap(_.transactions)
            .getOrElse(List.empty)
            .flatMap(mapInsiderTransaction)
            .sorted,
          insiderRoster = data.insiderHolders
            .flatMap(_.holders)
            .getOrElse(List.empty)
            .flatMap(mapInsiderRosterEntry)
            .sorted
        )
      }

    private def mapMajorHolders(raw: MajorHoldersBreakdownRaw): Option[MajorHolders] =
      for {
        insiders <- raw.insidersPercentHeld.map(_.raw)
        institutions <- raw.institutionsPercentHeld.map(_.raw)
        institutionsFloat <- raw.institutionsFloatPercentHeld.map(_.raw)
        institutionsCount <- raw.institutionsCount.map(_.raw)
      } yield MajorHolders(insiders, institutions, institutionsFloat, institutionsCount)

    private def mapInstitutionalHolder(raw: OwnershipEntryRaw): Option[InstitutionalHolder] =
      for {
        org <- raw.organization
        reportDate <- raw.reportDate.map(v => epochToLocalDate(v.raw))
        pctHeld <- raw.pctHeld.map(_.raw)
        position <- raw.position.map(_.raw)
        value <- raw.value.map(_.raw)
      } yield InstitutionalHolder(org, reportDate, pctHeld, position, value)

    private def mapMutualFundHolder(raw: OwnershipEntryRaw): Option[MutualFundHolder] =
      for {
        org <- raw.organization
        reportDate <- raw.reportDate.map(v => epochToLocalDate(v.raw))
        pctHeld <- raw.pctHeld.map(_.raw)
        position <- raw.position.map(_.raw)
        value <- raw.value.map(_.raw)
      } yield MutualFundHolder(org, reportDate, pctHeld, position, value)

    private def mapInsiderTransaction(raw: InsiderTransactionRaw): Option[InsiderTransaction] =
      for {
        filerName <- raw.filerName
        filerRelation <- raw.filerRelation
        transactionDate <- raw.startDate.map(v => epochToLocalDate(v.raw))
        shares <- raw.shares.map(_.raw)
      } yield InsiderTransaction(
        filerName = filerName,
        filerRelation = filerRelation,
        transactionDate = transactionDate,
        shares = shares,
        value = raw.value.map(_.raw),
        transactionText = raw.transactionText.getOrElse(""),
        ownershipType = raw.ownership.map(OwnershipType.fromString).getOrElse(OwnershipType.Direct),
        filerUrl = raw.filerUrl.filter(_.nonEmpty)
      )

    private def mapInsiderRosterEntry(raw: InsiderHolderRaw): Option[InsiderRosterEntry] =
      for {
        name <- raw.name
        relation <- raw.relation
      } yield InsiderRosterEntry(
        name = name,
        relation = relation,
        latestTransactionDate = raw.latestTransDate.map(v => epochToLocalDate(v.raw)),
        latestTransactionType = raw.transactionDescription,
        positionDirect = raw.positionDirect.map(_.raw),
        positionDirectDate = raw.positionDirectDate.map(v => epochToLocalDate(v.raw)),
        positionIndirect = raw.positionIndirect.map(_.raw),
        positionIndirectDate = raw.positionIndirectDate.map(v => epochToLocalDate(v.raw)),
        url = raw.url.filter(_.nonEmpty)
      )
  }
}
