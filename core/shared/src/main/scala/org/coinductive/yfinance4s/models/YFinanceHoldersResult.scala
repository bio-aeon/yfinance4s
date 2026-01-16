package org.coinductive.yfinance4s.models

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.coinductive.yfinance4s.models.YFinanceQuoteResult.Value

/** Raw API response for holders data from Quote Summary endpoint. */
private[yfinance4s] final case class YFinanceHoldersResult(
    quoteSummary: HoldersQuoteSummary
)

private[yfinance4s] object YFinanceHoldersResult {
  implicit val decoder: Decoder[YFinanceHoldersResult] = deriveDecoder
}

private[yfinance4s] final case class HoldersQuoteSummary(
    result: List[HoldersQuoteData],
    error: Option[String]
)

private[yfinance4s] object HoldersQuoteSummary {
  implicit val decoder: Decoder[HoldersQuoteSummary] = deriveDecoder
}

private[yfinance4s] final case class HoldersQuoteData(
    majorHoldersBreakdown: Option[MajorHoldersBreakdownRaw],
    institutionOwnership: Option[InstitutionOwnershipRaw],
    fundOwnership: Option[FundOwnershipRaw],
    insiderTransactions: Option[InsiderTransactionsRaw],
    insiderHolders: Option[InsiderHoldersRaw]
)

private[yfinance4s] object HoldersQuoteData {
  implicit val decoder: Decoder[HoldersQuoteData] = deriveDecoder
}

// --- Major Holders Breakdown ---

private[yfinance4s] final case class MajorHoldersBreakdownRaw(
    insidersPercentHeld: Option[Value[Double]],
    institutionsPercentHeld: Option[Value[Double]],
    institutionsFloatPercentHeld: Option[Value[Double]],
    institutionsCount: Option[Value[Int]]
)

private[yfinance4s] object MajorHoldersBreakdownRaw {
  implicit val decoder: Decoder[MajorHoldersBreakdownRaw] = deriveDecoder
}

// --- Institution Ownership ---

private[yfinance4s] final case class InstitutionOwnershipRaw(
    ownershipList: Option[List[OwnershipEntryRaw]]
)

private[yfinance4s] object InstitutionOwnershipRaw {
  implicit val decoder: Decoder[InstitutionOwnershipRaw] = deriveDecoder
}

private[yfinance4s] final case class OwnershipEntryRaw(
    reportDate: Option[Value[Long]],
    organization: Option[String],
    pctHeld: Option[Value[Double]],
    position: Option[Value[Long]],
    value: Option[Value[Long]]
)

private[yfinance4s] object OwnershipEntryRaw {
  implicit val decoder: Decoder[OwnershipEntryRaw] = deriveDecoder
}

// --- Fund Ownership ---

private[yfinance4s] final case class FundOwnershipRaw(
    ownershipList: Option[List[OwnershipEntryRaw]]
)

private[yfinance4s] object FundOwnershipRaw {
  implicit val decoder: Decoder[FundOwnershipRaw] = deriveDecoder
}

// --- Insider Transactions ---

private[yfinance4s] final case class InsiderTransactionsRaw(
    transactions: Option[List[InsiderTransactionRaw]]
)

private[yfinance4s] object InsiderTransactionsRaw {
  implicit val decoder: Decoder[InsiderTransactionsRaw] = deriveDecoder
}

private[yfinance4s] final case class InsiderTransactionRaw(
    shares: Option[Value[Long]],
    value: Option[Value[Long]],
    filerUrl: Option[String],
    transactionText: Option[String],
    filerName: Option[String],
    filerRelation: Option[String],
    startDate: Option[Value[Long]],
    ownership: Option[String]
)

private[yfinance4s] object InsiderTransactionRaw {
  implicit val decoder: Decoder[InsiderTransactionRaw] = deriveDecoder
}

// --- Insider Holders ---

private[yfinance4s] final case class InsiderHoldersRaw(
    holders: Option[List[InsiderHolderRaw]]
)

private[yfinance4s] object InsiderHoldersRaw {
  implicit val decoder: Decoder[InsiderHoldersRaw] = deriveDecoder
}

private[yfinance4s] final case class InsiderHolderRaw(
    name: Option[String],
    relation: Option[String],
    url: Option[String],
    transactionDescription: Option[String],
    latestTransDate: Option[Value[Long]],
    positionDirect: Option[Value[Long]],
    positionDirectDate: Option[Value[Long]],
    positionIndirect: Option[Value[Long]],
    positionIndirectDate: Option[Value[Long]]
)

private[yfinance4s] object InsiderHolderRaw {
  implicit val decoder: Decoder[InsiderHolderRaw] = deriveDecoder
}
