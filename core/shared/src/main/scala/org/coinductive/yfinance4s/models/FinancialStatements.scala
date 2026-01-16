package org.coinductive.yfinance4s.models

/** Container for all financial statements of a company.
  *
  * @param ticker
  *   The stock ticker symbol
  * @param currency
  *   The primary reporting currency
  * @param incomeStatements
  *   Income statements sorted by date (most recent first)
  * @param balanceSheets
  *   Balance sheets sorted by date (most recent first)
  * @param cashFlowStatements
  *   Cash flow statements sorted by date (most recent first)
  */
final case class FinancialStatements(
    ticker: Ticker,
    currency: String,
    incomeStatements: List[IncomeStatement],
    balanceSheets: List[BalanceSheet],
    cashFlowStatements: List[CashFlowStatement]
) {

  /** True if any financial data is available */
  def nonEmpty: Boolean =
    incomeStatements.nonEmpty || balanceSheets.nonEmpty || cashFlowStatements.nonEmpty

  /** True if no financial data is available */
  def isEmpty: Boolean = !nonEmpty

  /** Most recent income statement */
  def latestIncomeStatement: Option[IncomeStatement] = incomeStatements.headOption

  /** Most recent balance sheet */
  def latestBalanceSheet: Option[BalanceSheet] = balanceSheets.headOption

  /** Most recent cash flow statement */
  def latestCashFlowStatement: Option[CashFlowStatement] = cashFlowStatements.headOption

  /** Total number of reporting periods available */
  def periodCount: Int = Math.max(
    Math.max(incomeStatements.size, balanceSheets.size),
    cashFlowStatements.size
  )

  // --- Cross-Statement Metrics ---

  /** Return on Assets (Net Income / Total Assets) for most recent period */
  def returnOnAssets: Option[Double] =
    for {
      is <- latestIncomeStatement
      bs <- latestBalanceSheet
      ni <- is.netIncome
      assets <- bs.totalAssets if assets != 0
    } yield ni / assets

  /** Return on Equity (Net Income / Stockholders Equity) for most recent period */
  def returnOnEquity: Option[Double] =
    for {
      is <- latestIncomeStatement
      bs <- latestBalanceSheet
      ni <- is.netIncome
      equity <- bs.stockholdersEquity if equity != 0
    } yield ni / equity

  /** Asset Turnover (Revenue / Total Assets) for most recent period */
  def assetTurnover: Option[Double] =
    for {
      is <- latestIncomeStatement
      bs <- latestBalanceSheet
      rev <- is.totalRevenue
      assets <- bs.totalAssets if assets != 0
    } yield rev / assets

  /** Interest Coverage Ratio (EBIT / Interest Expense) for most recent period */
  def interestCoverage: Option[Double] =
    for {
      is <- latestIncomeStatement
      ebit <- is.ebit
      interest <- is.interestExpense if interest != 0
    } yield ebit / Math.abs(interest)
}

object FinancialStatements {

  /** Empty financial statements */
  def empty(ticker: Ticker): FinancialStatements = FinancialStatements(
    ticker = ticker,
    currency = "USD",
    incomeStatements = List.empty,
    balanceSheets = List.empty,
    cashFlowStatements = List.empty
  )
}
