package org.coinductive.yfinance4s

import cats.Monad
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import org.coinductive.yfinance4s.Mapping.*
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.internal.*

/** Algebra for analyst data (price targets, recommendations, estimates, etc.). */
trait Analysts[F[_]] {

  /** Retrieves analyst consensus price targets for a ticker. */
  def getAnalystPriceTargets(ticker: Ticker): F[Option[AnalystPriceTargets]]

  /** Retrieves analyst recommendation trends for a ticker. */
  def getRecommendations(ticker: Ticker): F[List[RecommendationTrend]]

  /** Retrieves analyst upgrade/downgrade history for a ticker. */
  def getUpgradeDowngradeHistory(ticker: Ticker): F[List[UpgradeDowngrade]]

  /** Retrieves analyst earnings (EPS) estimates for a ticker. */
  def getEarningsEstimates(ticker: Ticker): F[List[EarningsEstimate]]

  /** Retrieves analyst revenue estimates for a ticker. */
  def getRevenueEstimates(ticker: Ticker): F[List[RevenueEstimate]]

  /** Retrieves historical earnings (actual vs. estimate) for a ticker. */
  def getEarningsHistory(ticker: Ticker): F[List[EarningsHistory]]

  /** Retrieves growth estimates with index comparison for a ticker. */
  def getGrowthEstimates(ticker: Ticker): F[List[GrowthEstimates]]

  /** Retrieves comprehensive analyst data for a ticker. */
  def getAnalystData(ticker: Ticker): F[Option[AnalystData]]
}

private[yfinance4s] object Analysts {

  def apply[F[_]: Monad](gateway: YFinanceGateway[F], auth: YFinanceAuth[F]): Analysts[F] =
    new AnalystsImpl(gateway, auth)

  private final class AnalystsImpl[F[_]: Monad](
      gateway: YFinanceGateway[F],
      auth: YFinanceAuth[F]
  ) extends Analysts[F] {

    def getAnalystPriceTargets(ticker: Ticker): F[Option[AnalystPriceTargets]] =
      fetchAnalystData(ticker)(extractPriceTargets)

    def getRecommendations(ticker: Ticker): F[List[RecommendationTrend]] =
      fetchAnalystData(ticker)(extractRecommendations)

    def getUpgradeDowngradeHistory(ticker: Ticker): F[List[UpgradeDowngrade]] =
      fetchAnalystData(ticker)(extractUpgradeDowngrades)

    def getEarningsEstimates(ticker: Ticker): F[List[EarningsEstimate]] =
      fetchAnalystData(ticker)(extractEarningsEstimates)

    def getRevenueEstimates(ticker: Ticker): F[List[RevenueEstimate]] =
      fetchAnalystData(ticker)(extractRevenueEstimates)

    def getEarningsHistory(ticker: Ticker): F[List[EarningsHistory]] =
      fetchAnalystData(ticker)(extractEarningsHistoryEntries)

    def getGrowthEstimates(ticker: Ticker): F[List[GrowthEstimates]] =
      fetchAnalystData(ticker)(extractGrowthEstimates)

    def getAnalystData(ticker: Ticker): F[Option[AnalystData]] =
      fetchAnalystData(ticker)(mapToAnalystData)

    // --- Private Helpers ---

    private def fetchAnalystData[A](ticker: Ticker)(extract: AnalystQuoteSummary => A): F[A] =
      auth.getCredentials.flatMap(creds => gateway.getAnalystData(ticker, creds).map(extract))

    private def analystQuoteData(result: AnalystQuoteSummary): Option[AnalystQuoteData] =
      result.result.headOption

    private def earningsTrendEntries(result: AnalystQuoteSummary): List[EarningsTrendEntryRaw] =
      analystQuoteData(result).flatMap(_.earningsTrend).flatMap(_.trend).getOrElse(List.empty)

    private def extractPriceTargets(result: AnalystQuoteSummary): Option[AnalystPriceTargets] =
      analystQuoteData(result).flatMap(_.financialData).flatMap(mapPriceTargets)

    private def extractRecommendations(result: AnalystQuoteSummary): List[RecommendationTrend] =
      analystQuoteData(result)
        .flatMap(_.recommendationTrend)
        .flatMap(_.trend)
        .getOrElse(List.empty)
        .flatMap(mapRecommendationTrend)
        .sorted

    private def extractUpgradeDowngrades(result: AnalystQuoteSummary): List[UpgradeDowngrade] =
      analystQuoteData(result)
        .flatMap(_.upgradeDowngradeHistory)
        .flatMap(_.history)
        .getOrElse(List.empty)
        .flatMap(mapUpgradeDowngrade)
        .sorted

    private def extractEarningsEstimates(result: AnalystQuoteSummary): List[EarningsEstimate] =
      earningsTrendEntries(result).flatMap(mapEarningsEstimate).sorted

    private def extractRevenueEstimates(result: AnalystQuoteSummary): List[RevenueEstimate] =
      earningsTrendEntries(result).flatMap(mapRevenueEstimate).sorted

    private def extractEpsTrends(result: AnalystQuoteSummary): List[EpsTrend] =
      earningsTrendEntries(result).flatMap(mapEpsTrend).sorted

    private def extractEpsRevisionsList(result: AnalystQuoteSummary): List[EpsRevisions] =
      earningsTrendEntries(result).flatMap(mapEpsRevisions).sorted

    private def extractEarningsHistoryEntries(result: AnalystQuoteSummary): List[EarningsHistory] =
      analystQuoteData(result)
        .flatMap(_.earningsHistory)
        .flatMap(_.history)
        .getOrElse(List.empty)
        .flatMap(mapEarningsHistoryEntry)
        .sorted

    private def extractGrowthEstimates(result: AnalystQuoteSummary): List[GrowthEstimates] = {
      val data = analystQuoteData(result)
      val indexTrend = data.flatMap(_.indexTrend)
      val indexSymbol = indexTrend.flatMap(_.symbol)
      val indexGrowthByPeriod: Map[String, Double] = indexTrend
        .flatMap(_.estimates)
        .getOrElse(List.empty)
        .flatMap(e => for { p <- e.period; g <- e.growth.map(_.raw) } yield p -> g)
        .toMap

      earningsTrendEntries(result).flatMap { entry =>
        entry.period.map { period =>
          GrowthEstimates(
            period = period,
            stockGrowth = entry.growth.map(_.raw),
            indexGrowth = indexGrowthByPeriod.get(period),
            indexSymbol = indexSymbol
          )
        }
      }.sorted
    }

    private def mapToAnalystData(result: AnalystQuoteSummary): Option[AnalystData] =
      analystQuoteData(result).map { _ =>
        AnalystData(
          priceTargets = extractPriceTargets(result),
          recommendations = extractRecommendations(result),
          upgradeDowngradeHistory = extractUpgradeDowngrades(result),
          earningsEstimates = extractEarningsEstimates(result),
          revenueEstimates = extractRevenueEstimates(result),
          epsTrends = extractEpsTrends(result),
          epsRevisions = extractEpsRevisionsList(result),
          earningsHistory = extractEarningsHistoryEntries(result),
          growthEstimates = extractGrowthEstimates(result)
        )
      }

    private def mapPriceTargets(raw: AnalystFinancialDataRaw): Option[AnalystPriceTargets] =
      for {
        currentPrice <- raw.currentPrice.flatMap(_.raw)
        targetHigh <- raw.targetHighPrice.flatMap(_.raw)
        targetLow <- raw.targetLowPrice.flatMap(_.raw)
        targetMean <- raw.targetMeanPrice.flatMap(_.raw)
        targetMedian <- raw.targetMedianPrice.flatMap(_.raw)
        numAnalysts <- raw.numberOfAnalystOpinions.flatMap(_.raw)
        recKey <- raw.recommendationKey
        recMean <- raw.recommendationMean.flatMap(_.raw)
      } yield AnalystPriceTargets(
        currentPrice = currentPrice,
        targetHigh = targetHigh,
        targetLow = targetLow,
        targetMean = targetMean,
        targetMedian = targetMedian,
        numberOfAnalysts = numAnalysts,
        recommendationKey = recKey,
        recommendationMean = recMean
      )

    private def mapRecommendationTrend(raw: RecommendationTrendEntryRaw): Option[RecommendationTrend] =
      raw.period.map { period =>
        RecommendationTrend(
          period = period,
          strongBuy = raw.strongBuy.getOrElse(0),
          buy = raw.buy.getOrElse(0),
          hold = raw.hold.getOrElse(0),
          sell = raw.sell.getOrElse(0),
          strongSell = raw.strongSell.getOrElse(0)
        )
      }

    private def mapUpgradeDowngrade(raw: UpgradeDowngradeEntryRaw): Option[UpgradeDowngrade] =
      for {
        epochDate <- raw.epochGradeDate
        firm <- raw.firm
        toGrade <- raw.toGrade
        action <- raw.action
      } yield UpgradeDowngrade(
        date = epochToLocalDate(epochDate),
        firm = firm,
        toGrade = toGrade,
        fromGrade = raw.fromGrade.filter(_.nonEmpty),
        action = UpgradeDowngradeAction.fromString(action)
      )

    private def mapEarningsEstimate(raw: EarningsTrendEntryRaw): Option[EarningsEstimate] =
      raw.period.map { period =>
        EarningsEstimate(
          period = period,
          endDate = raw.endDate,
          avg = raw.earningsEstimate.flatMap(_.avg).map(_.raw),
          low = raw.earningsEstimate.flatMap(_.low).map(_.raw),
          high = raw.earningsEstimate.flatMap(_.high).map(_.raw),
          yearAgoEps = raw.earningsEstimate.flatMap(_.yearAgoEps).map(_.raw),
          numberOfAnalysts = raw.earningsEstimate.flatMap(_.numberOfAnalysts).map(_.raw),
          growth = raw.earningsEstimate.flatMap(_.growth).map(_.raw)
        )
      }

    private def mapRevenueEstimate(raw: EarningsTrendEntryRaw): Option[RevenueEstimate] =
      raw.period.map { period =>
        RevenueEstimate(
          period = period,
          endDate = raw.endDate,
          avg = raw.revenueEstimate.flatMap(_.avg).map(_.raw),
          low = raw.revenueEstimate.flatMap(_.low).map(_.raw),
          high = raw.revenueEstimate.flatMap(_.high).map(_.raw),
          numberOfAnalysts = raw.revenueEstimate.flatMap(_.numberOfAnalysts).map(_.raw),
          yearAgoRevenue = raw.revenueEstimate.flatMap(_.yearAgoRevenue).map(_.raw),
          growth = raw.revenueEstimate.flatMap(_.growth).map(_.raw)
        )
      }

    private def mapEpsTrend(raw: EarningsTrendEntryRaw): Option[EpsTrend] =
      raw.period.map { period =>
        EpsTrend(
          period = period,
          current = raw.epsTrend.flatMap(_.current).map(_.raw),
          sevenDaysAgo = raw.epsTrend.flatMap(_.sevenDaysAgo).map(_.raw),
          thirtyDaysAgo = raw.epsTrend.flatMap(_.thirtyDaysAgo).map(_.raw),
          sixtyDaysAgo = raw.epsTrend.flatMap(_.sixtyDaysAgo).map(_.raw),
          ninetyDaysAgo = raw.epsTrend.flatMap(_.ninetyDaysAgo).map(_.raw)
        )
      }

    private def mapEpsRevisions(raw: EarningsTrendEntryRaw): Option[EpsRevisions] =
      raw.period.map { period =>
        EpsRevisions(
          period = period,
          upLast7Days = raw.epsRevisions.flatMap(_.upLast7days).flatMap(_.raw),
          upLast30Days = raw.epsRevisions.flatMap(_.upLast30days).flatMap(_.raw),
          downLast30Days = raw.epsRevisions.flatMap(_.downLast30days).flatMap(_.raw),
          downLast90Days = raw.epsRevisions.flatMap(_.downLast90days).flatMap(_.raw)
        )
      }

    private def mapEarningsHistoryEntry(raw: EarningsHistoryEntryRaw): Option[EarningsHistory] =
      for {
        quarter <- raw.quarter.map(v => epochToLocalDate(v.raw))
        period <- raw.period
      } yield EarningsHistory(
        quarter = quarter,
        period = period,
        epsActual = raw.epsActual.map(_.raw),
        epsEstimate = raw.epsEstimate.map(_.raw),
        epsDifference = raw.epsDifference.map(_.raw),
        surprisePercent = raw.surprisePercent.map(_.raw)
      )
  }
}
