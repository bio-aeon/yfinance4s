package org.coinductive.yfinance4s.models.internal

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.coinductive.yfinance4s.models.internal.YFinanceQuoteResult.Value

// --- Top-level result wrapper ---

private[yfinance4s] final case class YFinanceSectorResult(
    data: SectorDataRaw
)

private[yfinance4s] object YFinanceSectorResult {
  implicit val decoder: Decoder[YFinanceSectorResult] = deriveDecoder
}

// --- Sector data container ---

private[yfinance4s] final case class SectorDataRaw(
    name: Option[String],
    symbol: Option[String],
    overview: Option[SectorOverviewRaw],
    topCompanies: Option[List[TopCompanyRaw]],
    topETFs: Option[List[FundEntryRaw]],
    topMutualFunds: Option[List[FundEntryRaw]],
    industries: Option[List[IndustryEntryRaw]],
    researchReports: Option[List[ResearchReportRaw]]
)

private[yfinance4s] object SectorDataRaw {
  implicit val decoder: Decoder[SectorDataRaw] = deriveDecoder
}

// --- Overview ---

private[yfinance4s] final case class SectorOverviewRaw(
    companiesCount: Option[Int],
    marketCap: Option[Value[Long]],
    messageBoardId: Option[String],
    description: Option[String],
    industriesCount: Option[Int],
    marketWeight: Option[Value[Double]],
    employeeCount: Option[Value[Long]]
)

private[yfinance4s] object SectorOverviewRaw {
  implicit val decoder: Decoder[SectorOverviewRaw] = deriveDecoder
}

// --- Top companies ---

private[yfinance4s] final case class TopCompanyRaw(
    symbol: Option[String],
    name: Option[String],
    rating: Option[String],
    marketWeight: Option[Value[Double]]
)

private[yfinance4s] object TopCompanyRaw {
  implicit val decoder: Decoder[TopCompanyRaw] = deriveDecoder
}

// --- ETFs and mutual funds (shared shape) ---

private[yfinance4s] final case class FundEntryRaw(
    symbol: Option[String],
    name: Option[String]
)

private[yfinance4s] object FundEntryRaw {
  implicit val decoder: Decoder[FundEntryRaw] = deriveDecoder
}

// --- Industries ---

private[yfinance4s] final case class IndustryEntryRaw(
    key: Option[String],
    name: Option[String],
    symbol: Option[String],
    marketWeight: Option[Value[Double]]
)

private[yfinance4s] object IndustryEntryRaw {
  implicit val decoder: Decoder[IndustryEntryRaw] = deriveDecoder
}

// --- Research reports ---

private[yfinance4s] final case class ResearchReportRaw(
    reportTitle: Option[String],
    provider: Option[String],
    reportDate: Option[String]
)

private[yfinance4s] object ResearchReportRaw {
  implicit val decoder: Decoder[ResearchReportRaw] = deriveDecoder
}
