package org.coinductive.yfinance4s.models

import java.time.LocalDate

/** Income statement data for a reporting period.
  *
  * All monetary values are in the company's reporting currency. Values are typically in the base unit (e.g., USD, not
  * millions).
  *
  * @param reportDate
  *   The fiscal period end date
  * @param periodType
  *   The period type (e.g., "12M" for annual, "3M" for quarterly)
  * @param currencyCode
  *   The currency of monetary values (e.g., "USD")
  */
final case class IncomeStatement(
    reportDate: LocalDate,
    periodType: String,
    currencyCode: String,
    // Revenue
    totalRevenue: Option[Double],
    operatingRevenue: Option[Double],
    costOfRevenue: Option[Double],
    grossProfit: Option[Double],
    // Operating Expenses
    operatingExpense: Option[Double],
    sellingGeneralAndAdministration: Option[Double],
    researchAndDevelopment: Option[Double],
    depreciationAndAmortization: Option[Double],
    // Operating Income
    operatingIncome: Option[Double],
    // Non-Operating Items
    interestIncome: Option[Double],
    interestExpense: Option[Double],
    netInterestIncome: Option[Double],
    otherIncomeExpense: Option[Double],
    // Pre-Tax & Tax
    pretaxIncome: Option[Double],
    taxProvision: Option[Double],
    // Net Income
    netIncome: Option[Double],
    netIncomeContinuousOperations: Option[Double],
    netIncomeCommonStockholders: Option[Double],
    // EPS
    basicEps: Option[Double],
    dilutedEps: Option[Double],
    basicAverageShares: Option[Double],
    dilutedAverageShares: Option[Double],
    // EBITDA
    ebit: Option[Double],
    ebitda: Option[Double]
) extends Ordered[IncomeStatement] {

  /** Compare by report date (descending - most recent first) */
  def compare(that: IncomeStatement): Int = that.reportDate.compareTo(this.reportDate)

  // --- Derived Metrics ---

  /** Gross profit margin: Gross Profit / Total Revenue */
  def grossMargin: Option[Double] =
    for {
      gp <- grossProfit
      rev <- totalRevenue if rev != 0
    } yield gp / rev

  /** Operating profit margin: Operating Income / Total Revenue */
  def operatingMargin: Option[Double] =
    for {
      oi <- operatingIncome
      rev <- totalRevenue if rev != 0
    } yield oi / rev

  /** Net profit margin: Net Income / Total Revenue */
  def netMargin: Option[Double] =
    for {
      ni <- netIncome
      rev <- totalRevenue if rev != 0
    } yield ni / rev

  /** EBITDA margin: EBITDA / Total Revenue */
  def ebitdaMargin: Option[Double] =
    for {
      eb <- ebitda
      rev <- totalRevenue if rev != 0
    } yield eb / rev

  /** Effective tax rate: Tax Provision / Pretax Income */
  def effectiveTaxRate: Option[Double] =
    for {
      tax <- taxProvision
      pretax <- pretaxIncome if pretax != 0
    } yield tax / pretax
}

object IncomeStatement {

  /** Key fields to request from the API (without timescale prefix) */
  val apiKeys: List[String] = List(
    "TotalRevenue",
    "OperatingRevenue",
    "CostOfRevenue",
    "GrossProfit",
    "OperatingExpense",
    "SellingGeneralAndAdministration",
    "ResearchAndDevelopment",
    "DepreciationAndAmortizationInIncomeStatement",
    "OperatingIncome",
    "InterestIncome",
    "InterestExpense",
    "NetInterestIncome",
    "OtherIncomeExpense",
    "PretaxIncome",
    "TaxProvision",
    "NetIncome",
    "NetIncomeContinuousOperations",
    "NetIncomeCommonStockholders",
    "BasicEPS",
    "DilutedEPS",
    "BasicAverageShares",
    "DilutedAverageShares",
    "EBIT",
    "EBITDA"
  )
}
