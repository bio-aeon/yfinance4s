package org.coinductive.yfinance4s.models.internal

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.coinductive.yfinance4s.models.internal.YFinanceQuoteResult.Value

/** Yahoo Finance `/v1/finance/industries/{key}` payload.
  *
  * Matches the inner object of Yahoo's `{"data": {…}}` envelope directly; the companion decoder navigates the `data`
  * field before applying the derived field-based decoder.
  */
private[yfinance4s] final case class YFinanceIndustryResult(
    name: String,
    symbol: Option[String],
    sectorKey: String,
    sectorName: String,
    overview: Option[SectorOverviewRaw],
    topCompanies: Option[List[TopCompanyRaw]],
    topPerformingCompanies: Option[List[TopPerformingCompanyRaw]],
    topGrowthCompanies: Option[List[TopGrowthCompanyRaw]],
    researchReports: Option[List[ResearchReportRaw]]
)

private[yfinance4s] object YFinanceIndustryResult {
  implicit val decoder: Decoder[YFinanceIndustryResult] = {
    val inner: Decoder[YFinanceIndustryResult] = deriveDecoder
    inner.prepare(_.downField("data"))
  }
}

// --- Top performing companies ---

private[yfinance4s] final case class TopPerformingCompanyRaw(
    symbol: String,
    name: Option[String],
    ytdReturn: Option[Value[Double]],
    lastPrice: Option[Value[Double]],
    targetPrice: Option[Value[Double]]
)

private[yfinance4s] object TopPerformingCompanyRaw {
  implicit val decoder: Decoder[TopPerformingCompanyRaw] = deriveDecoder
}

// --- Top growth companies ---

private[yfinance4s] final case class TopGrowthCompanyRaw(
    symbol: String,
    name: Option[String],
    ytdReturn: Option[Value[Double]],
    growthEstimate: Option[Value[Double]]
)

private[yfinance4s] object TopGrowthCompanyRaw {
  implicit val decoder: Decoder[TopGrowthCompanyRaw] = deriveDecoder
}
