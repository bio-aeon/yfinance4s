package org.coinductive.yfinance4s.models

import io.circe.{Decoder, HCursor}
import io.circe.generic.semiauto.deriveDecoder
import org.coinductive.yfinance4s.models.YFinanceQuoteResult.Value

// --- Top-level result wrapper ---

private[yfinance4s] final case class YFinanceAnalystResult(
    quoteSummary: AnalystQuoteSummary
)

private[yfinance4s] object YFinanceAnalystResult {
  implicit val decoder: Decoder[YFinanceAnalystResult] = deriveDecoder
}

private[yfinance4s] final case class AnalystQuoteSummary(
    result: List[AnalystQuoteData],
    error: Option[String]
)

private[yfinance4s] object AnalystQuoteSummary {
  implicit val decoder: Decoder[AnalystQuoteSummary] = deriveDecoder
}

private[yfinance4s] final case class AnalystQuoteData(
    financialData: Option[AnalystFinancialDataRaw],
    recommendationTrend: Option[RecommendationTrendRaw],
    upgradeDowngradeHistory: Option[UpgradeDowngradeHistoryRaw],
    earningsTrend: Option[EarningsTrendRaw],
    earningsHistory: Option[EarningsHistoryContainerRaw],
    indexTrend: Option[IndexTrendRaw]
)

private[yfinance4s] object AnalystQuoteData {
  implicit val decoder: Decoder[AnalystQuoteData] = deriveDecoder
}

// --- financialData (price targets subset) ---

private[yfinance4s] final case class AnalystFinancialDataRaw(
    targetHighPrice: Option[Value[Double]],
    targetLowPrice: Option[Value[Double]],
    targetMeanPrice: Option[Value[Double]],
    targetMedianPrice: Option[Value[Double]],
    currentPrice: Option[Value[Double]],
    numberOfAnalystOpinions: Option[Value[Int]],
    recommendationKey: Option[String],
    recommendationMean: Option[Value[Double]]
)

private[yfinance4s] object AnalystFinancialDataRaw {
  implicit val decoder: Decoder[AnalystFinancialDataRaw] = deriveDecoder
}

// --- recommendationTrend ---

private[yfinance4s] final case class RecommendationTrendRaw(
    trend: Option[List[RecommendationTrendEntryRaw]]
)

private[yfinance4s] object RecommendationTrendRaw {
  implicit val decoder: Decoder[RecommendationTrendRaw] = deriveDecoder
}

private[yfinance4s] final case class RecommendationTrendEntryRaw(
    period: Option[String],
    strongBuy: Option[Int],
    buy: Option[Int],
    hold: Option[Int],
    sell: Option[Int],
    strongSell: Option[Int]
)

private[yfinance4s] object RecommendationTrendEntryRaw {
  implicit val decoder: Decoder[RecommendationTrendEntryRaw] = deriveDecoder
}

// --- upgradeDowngradeHistory ---

private[yfinance4s] final case class UpgradeDowngradeHistoryRaw(
    history: Option[List[UpgradeDowngradeEntryRaw]]
)

private[yfinance4s] object UpgradeDowngradeHistoryRaw {
  implicit val decoder: Decoder[UpgradeDowngradeHistoryRaw] = deriveDecoder
}

private[yfinance4s] final case class UpgradeDowngradeEntryRaw(
    epochGradeDate: Option[Long],
    firm: Option[String],
    toGrade: Option[String],
    fromGrade: Option[String],
    action: Option[String]
)

private[yfinance4s] object UpgradeDowngradeEntryRaw {
  implicit val decoder: Decoder[UpgradeDowngradeEntryRaw] = deriveDecoder
}

// --- earningsTrend ---

private[yfinance4s] final case class EarningsTrendRaw(
    trend: Option[List[EarningsTrendEntryRaw]]
)

private[yfinance4s] object EarningsTrendRaw {
  implicit val decoder: Decoder[EarningsTrendRaw] = deriveDecoder
}

private[yfinance4s] final case class EarningsTrendEntryRaw(
    period: Option[String],
    endDate: Option[String],
    growth: Option[Value[Double]],
    earningsEstimate: Option[EarningsEstimateRaw],
    revenueEstimate: Option[RevenueEstimateRaw],
    epsTrend: Option[EpsTrendRaw],
    epsRevisions: Option[EpsRevisionsRaw]
)

private[yfinance4s] object EarningsTrendEntryRaw {
  implicit val decoder: Decoder[EarningsTrendEntryRaw] = deriveDecoder
}

private[yfinance4s] final case class EarningsEstimateRaw(
    avg: Option[Value[Double]],
    low: Option[Value[Double]],
    high: Option[Value[Double]],
    yearAgoEps: Option[Value[Double]],
    numberOfAnalysts: Option[Value[Int]],
    growth: Option[Value[Double]]
)

private[yfinance4s] object EarningsEstimateRaw {
  implicit val decoder: Decoder[EarningsEstimateRaw] = deriveDecoder
}

private[yfinance4s] final case class RevenueEstimateRaw(
    avg: Option[Value[Long]],
    low: Option[Value[Long]],
    high: Option[Value[Long]],
    numberOfAnalysts: Option[Value[Int]],
    yearAgoRevenue: Option[Value[Long]],
    growth: Option[Value[Double]]
)

private[yfinance4s] object RevenueEstimateRaw {
  implicit val decoder: Decoder[RevenueEstimateRaw] = deriveDecoder
}

// Custom decoder needed: "7daysAgo" etc. are not valid Scala identifiers
private[yfinance4s] final case class EpsTrendRaw(
    current: Option[Value[Double]],
    sevenDaysAgo: Option[Value[Double]],
    thirtyDaysAgo: Option[Value[Double]],
    sixtyDaysAgo: Option[Value[Double]],
    ninetyDaysAgo: Option[Value[Double]]
)

private[yfinance4s] object EpsTrendRaw {
  implicit val decoder: Decoder[EpsTrendRaw] = (c: HCursor) =>
    for {
      current <- c.downField("current").as[Option[Value[Double]]]
      sevenDaysAgo <- c.downField("7daysAgo").as[Option[Value[Double]]]
      thirtyDaysAgo <- c.downField("30daysAgo").as[Option[Value[Double]]]
      sixtyDaysAgo <- c.downField("60daysAgo").as[Option[Value[Double]]]
      ninetyDaysAgo <- c.downField("90daysAgo").as[Option[Value[Double]]]
    } yield EpsTrendRaw(current, sevenDaysAgo, thirtyDaysAgo, sixtyDaysAgo, ninetyDaysAgo)
}

private[yfinance4s] final case class EpsRevisionsRaw(
    upLast7days: Option[Value[Int]],
    upLast30days: Option[Value[Int]],
    downLast30days: Option[Value[Int]],
    downLast90days: Option[Value[Int]]
)

private[yfinance4s] object EpsRevisionsRaw {
  implicit val decoder: Decoder[EpsRevisionsRaw] = (c: HCursor) =>
    for {
      up7 <- c.downField("upLast7days").as[Option[Value[Int]]].orElse(Right(None))
      up30 <- c.downField("upLast30days").as[Option[Value[Int]]].orElse(Right(None))
      down30 <- c.downField("downLast30days").as[Option[Value[Int]]].orElse(Right(None))
      down90 <- c.downField("downLast90days").as[Option[Value[Int]]].orElse(Right(None))
    } yield EpsRevisionsRaw(up7, up30, down30, down90)
}

// --- earningsHistory ---

private[yfinance4s] final case class EarningsHistoryContainerRaw(
    history: Option[List[EarningsHistoryEntryRaw]]
)

private[yfinance4s] object EarningsHistoryContainerRaw {
  implicit val decoder: Decoder[EarningsHistoryContainerRaw] = deriveDecoder
}

private[yfinance4s] final case class EarningsHistoryEntryRaw(
    epsActual: Option[Value[Double]],
    epsEstimate: Option[Value[Double]],
    epsDifference: Option[Value[Double]],
    surprisePercent: Option[Value[Double]],
    quarter: Option[Value[Long]],
    period: Option[String]
)

private[yfinance4s] object EarningsHistoryEntryRaw {
  implicit val decoder: Decoder[EarningsHistoryEntryRaw] = deriveDecoder
}

// --- indexTrend ---

private[yfinance4s] final case class IndexTrendRaw(
    symbol: Option[String],
    estimates: Option[List[IndexTrendEstimateRaw]]
)

private[yfinance4s] object IndexTrendRaw {
  implicit val decoder: Decoder[IndexTrendRaw] = deriveDecoder
}

private[yfinance4s] final case class IndexTrendEstimateRaw(
    period: Option[String],
    growth: Option[Value[Double]]
)

private[yfinance4s] object IndexTrendEstimateRaw {
  implicit val decoder: Decoder[IndexTrendEstimateRaw] = deriveDecoder
}
