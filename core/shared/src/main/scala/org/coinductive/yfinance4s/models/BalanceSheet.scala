package org.coinductive.yfinance4s.models

import java.time.LocalDate

/** Balance sheet data for a reporting period.
  *
  * All monetary values are in the company's reporting currency.
  *
  * @param reportDate
  *   The fiscal period end date
  * @param periodType
  *   The period type (e.g., "12M" for annual, "3M" for quarterly)
  * @param currencyCode
  *   The currency of monetary values (e.g., "USD")
  */
final case class BalanceSheet(
    reportDate: LocalDate,
    periodType: String,
    currencyCode: String,
    // Assets - Current
    totalAssets: Option[Double],
    currentAssets: Option[Double],
    cashAndCashEquivalents: Option[Double],
    shortTermInvestments: Option[Double],
    accountsReceivable: Option[Double],
    inventory: Option[Double],
    otherCurrentAssets: Option[Double],
    // Assets - Non-Current
    totalNonCurrentAssets: Option[Double],
    netPpe: Option[Double],
    grossPpe: Option[Double],
    accumulatedDepreciation: Option[Double],
    goodwill: Option[Double],
    otherIntangibleAssets: Option[Double],
    longTermInvestments: Option[Double],
    otherNonCurrentAssets: Option[Double],
    // Liabilities - Current
    currentLiabilities: Option[Double],
    accountsPayable: Option[Double],
    currentDebt: Option[Double],
    otherCurrentLiabilities: Option[Double],
    // Liabilities - Non-Current
    totalNonCurrentLiabilities: Option[Double],
    longTermDebt: Option[Double],
    otherNonCurrentLiabilities: Option[Double],
    // Total Liabilities
    totalLiabilities: Option[Double],
    totalDebt: Option[Double],
    netDebt: Option[Double],
    // Stockholders Equity
    stockholdersEquity: Option[Double],
    commonStock: Option[Double],
    additionalPaidInCapital: Option[Double],
    retainedEarnings: Option[Double],
    treasuryStock: Option[Double],
    // Share Information
    sharesIssued: Option[Double],
    ordinarySharesNumber: Option[Double],
    // Derived Metrics from API
    workingCapital: Option[Double],
    tangibleBookValue: Option[Double],
    investedCapital: Option[Double]
) extends Ordered[BalanceSheet] {

  /** Compare by report date (descending - most recent first) */
  def compare(that: BalanceSheet): Int = that.reportDate.compareTo(this.reportDate)

  // --- Derived Metrics ---

  /** Current ratio: Current Assets / Current Liabilities */
  def currentRatio: Option[Double] =
    for {
      ca <- currentAssets
      cl <- currentLiabilities if cl != 0
    } yield ca / cl

  /** Quick ratio: (Current Assets - Inventory) / Current Liabilities */
  def quickRatio: Option[Double] =
    for {
      ca <- currentAssets
      inv <- inventory.orElse(Some(0.0))
      cl <- currentLiabilities if cl != 0
    } yield (ca - inv) / cl

  /** Debt to equity ratio: Total Debt / Stockholders Equity */
  def debtToEquity: Option[Double] =
    for {
      debt <- totalDebt
      equity <- stockholdersEquity if equity != 0
    } yield debt / equity

  /** Debt to assets ratio: Total Debt / Total Assets */
  def debtToAssets: Option[Double] =
    for {
      debt <- totalDebt
      assets <- totalAssets if assets != 0
    } yield debt / assets

  /** Book value per share: Stockholders Equity / Shares Outstanding */
  def bookValuePerShare: Option[Double] =
    for {
      equity <- stockholdersEquity
      shares <- ordinarySharesNumber if shares != 0
    } yield equity / shares

  /** Tangible book value per share: Tangible Book Value / Shares Outstanding */
  def tangibleBookValuePerShare: Option[Double] =
    for {
      tbv <- tangibleBookValue
      shares <- ordinarySharesNumber if shares != 0
    } yield tbv / shares
}

object BalanceSheet {

  /** Key fields to request from the API (without timescale prefix) */
  val apiKeys: List[String] = List(
    "TotalAssets",
    "CurrentAssets",
    "CashAndCashEquivalents",
    "OtherShortTermInvestments",
    "AccountsReceivable",
    "Inventory",
    "OtherCurrentAssets",
    "TotalNonCurrentAssets",
    "NetPPE",
    "GrossPPE",
    "AccumulatedDepreciation",
    "Goodwill",
    "OtherIntangibleAssets",
    "LongTermEquityInvestment",
    "OtherNonCurrentAssets",
    "CurrentLiabilities",
    "AccountsPayable",
    "CurrentDebt",
    "OtherCurrentLiabilities",
    "TotalNonCurrentLiabilitiesNetMinorityInterest",
    "LongTermDebt",
    "OtherNonCurrentLiabilities",
    "TotalLiabilitiesNetMinorityInterest",
    "TotalDebt",
    "NetDebt",
    "StockholdersEquity",
    "CommonStock",
    "AdditionalPaidInCapital",
    "RetainedEarnings",
    "TreasuryStock",
    "ShareIssued",
    "OrdinarySharesNumber",
    "WorkingCapital",
    "TangibleBookValue",
    "InvestedCapital"
  )
}
