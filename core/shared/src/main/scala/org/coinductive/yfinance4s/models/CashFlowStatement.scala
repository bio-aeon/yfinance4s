package org.coinductive.yfinance4s.models

import java.time.LocalDate

/** Cash flow statement data for a reporting period.
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
final case class CashFlowStatement(
    reportDate: LocalDate,
    periodType: String,
    currencyCode: String,
    // Operating Activities
    operatingCashFlow: Option[Double],
    netIncomeFromContinuingOperations: Option[Double],
    depreciationAmortizationDepletion: Option[Double],
    stockBasedCompensation: Option[Double],
    deferredIncomeTax: Option[Double],
    changeInWorkingCapital: Option[Double],
    changeInReceivables: Option[Double],
    changeInInventory: Option[Double],
    changeInPayables: Option[Double],
    // Investing Activities
    investingCashFlow: Option[Double],
    capitalExpenditure: Option[Double],
    purchaseOfInvestment: Option[Double],
    saleOfInvestment: Option[Double],
    purchaseOfBusiness: Option[Double],
    saleOfBusiness: Option[Double],
    netPpePurchaseAndSale: Option[Double],
    // Financing Activities
    financingCashFlow: Option[Double],
    issuanceOfDebt: Option[Double],
    repaymentOfDebt: Option[Double],
    issuanceOfCapitalStock: Option[Double],
    repurchaseOfCapitalStock: Option[Double],
    cashDividendsPaid: Option[Double],
    // Net Change in Cash
    beginningCashPosition: Option[Double],
    endCashPosition: Option[Double],
    changesInCash: Option[Double],
    effectOfExchangeRateChanges: Option[Double],
    // Free Cash Flow
    freeCashFlow: Option[Double]
) extends Ordered[CashFlowStatement] {

  /** Compare by report date (descending - most recent first) */
  def compare(that: CashFlowStatement): Int = that.reportDate.compareTo(this.reportDate)

  // --- Derived Metrics ---

  /** Free cash flow (calculated): Operating Cash Flow - Capital Expenditure */
  def calculatedFreeCashFlow: Option[Double] =
    for {
      ocf <- operatingCashFlow
      capex <- capitalExpenditure
    } yield ocf - Math.abs(capex)

  /** Free cash flow yield: Free Cash Flow / Market Cap (requires external market cap) */
  def freeCashFlowYield(marketCap: Double): Option[Double] =
    freeCashFlow.orElse(calculatedFreeCashFlow).map(_ / marketCap)

  /** Operating cash flow to net income ratio */
  def cashFlowToNetIncomeRatio: Option[Double] =
    for {
      ocf <- operatingCashFlow
      ni <- netIncomeFromContinuingOperations if ni != 0
    } yield ocf / ni

  /** Capital expenditure as percentage of operating cash flow */
  def capexToOperatingCashFlow: Option[Double] =
    for {
      capex <- capitalExpenditure
      ocf <- operatingCashFlow if ocf != 0
    } yield Math.abs(capex) / ocf

  /** Net debt repayment (positive = paid down debt, negative = took on more debt) */
  def netDebtChange: Option[Double] =
    for {
      repayment <- repaymentOfDebt.orElse(Some(0.0))
      issuance <- issuanceOfDebt.orElse(Some(0.0))
    } yield Math.abs(repayment) - Math.abs(issuance)
}

object CashFlowStatement {

  /** Key fields to request from the API (without timescale prefix) */
  val apiKeys: List[String] = List(
    "OperatingCashFlow",
    "NetIncomeFromContinuingOperations",
    "DepreciationAmortizationDepletion",
    "StockBasedCompensation",
    "DeferredIncomeTax",
    "ChangeInWorkingCapital",
    "ChangeInReceivables",
    "ChangeInInventory",
    "ChangeInPayable",
    "InvestingCashFlow",
    "CapitalExpenditure",
    "PurchaseOfInvestment",
    "SaleOfInvestment",
    "PurchaseOfBusiness",
    "SaleOfBusiness",
    "NetPPEPurchaseAndSale",
    "FinancingCashFlow",
    "IssuanceOfDebt",
    "RepaymentOfDebt",
    "IssuanceOfCapitalStock",
    "RepurchaseOfCapitalStock",
    "CashDividendsPaid",
    "BeginningCashPosition",
    "EndCashPosition",
    "ChangesInCash",
    "EffectOfExchangeRateChanges",
    "FreeCashFlow"
  )
}
