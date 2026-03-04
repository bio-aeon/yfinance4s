package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.*

import java.time.LocalDate

class FinancialStatementsSpec extends FunSuite {

  private val sampleIncomeStatement = IncomeStatement(
    reportDate = LocalDate.of(2024, 9, 28),
    periodType = "12M",
    currencyCode = "USD",
    totalRevenue = Some(383285000000.0),
    operatingRevenue = None,
    costOfRevenue = None,
    grossProfit = None,
    operatingExpense = None,
    sellingGeneralAndAdministration = None,
    researchAndDevelopment = None,
    depreciationAndAmortization = None,
    operatingIncome = None,
    interestIncome = None,
    interestExpense = Some(3000000000.0),
    netInterestIncome = None,
    otherIncomeExpense = None,
    pretaxIncome = None,
    taxProvision = None,
    netIncome = Some(94663000000.0),
    netIncomeContinuousOperations = None,
    netIncomeCommonStockholders = None,
    basicEps = None,
    dilutedEps = None,
    basicAverageShares = None,
    dilutedAverageShares = None,
    ebit = Some(113033000000.0),
    ebitda = None
  )

  private val sampleBalanceSheet = BalanceSheet(
    reportDate = LocalDate.of(2024, 9, 28),
    periodType = "12M",
    currencyCode = "USD",
    totalAssets = Some(364980000000.0),
    currentAssets = None,
    cashAndCashEquivalents = None,
    shortTermInvestments = None,
    accountsReceivable = None,
    inventory = None,
    otherCurrentAssets = None,
    totalNonCurrentAssets = None,
    netPpe = None,
    grossPpe = None,
    accumulatedDepreciation = None,
    goodwill = None,
    otherIntangibleAssets = None,
    longTermInvestments = None,
    otherNonCurrentAssets = None,
    currentLiabilities = None,
    accountsPayable = None,
    currentDebt = None,
    otherCurrentLiabilities = None,
    totalNonCurrentLiabilities = None,
    longTermDebt = None,
    otherNonCurrentLiabilities = None,
    totalLiabilities = None,
    totalDebt = None,
    netDebt = None,
    stockholdersEquity = Some(56950000000.0),
    commonStock = None,
    additionalPaidInCapital = None,
    retainedEarnings = None,
    treasuryStock = None,
    sharesIssued = None,
    ordinarySharesNumber = None,
    workingCapital = None,
    tangibleBookValue = None,
    investedCapital = None
  )

  private val sampleCashFlowStatement = CashFlowStatement(
    reportDate = LocalDate.of(2024, 9, 28),
    periodType = "12M",
    currencyCode = "USD",
    operatingCashFlow = Some(118254000000.0),
    netIncomeFromContinuingOperations = None,
    depreciationAmortizationDepletion = None,
    stockBasedCompensation = None,
    deferredIncomeTax = None,
    changeInWorkingCapital = None,
    changeInReceivables = None,
    changeInInventory = None,
    changeInPayables = None,
    investingCashFlow = None,
    capitalExpenditure = None,
    purchaseOfInvestment = None,
    saleOfInvestment = None,
    purchaseOfBusiness = None,
    saleOfBusiness = None,
    netPpePurchaseAndSale = None,
    financingCashFlow = None,
    issuanceOfDebt = None,
    repaymentOfDebt = None,
    issuanceOfCapitalStock = None,
    repurchaseOfCapitalStock = None,
    cashDividendsPaid = None,
    beginningCashPosition = None,
    endCashPosition = None,
    changesInCash = None,
    effectOfExchangeRateChanges = None,
    freeCashFlow = None
  )

  private val sampleFinancials = FinancialStatements(
    ticker = Ticker("AAPL"),
    currency = "USD",
    incomeStatements = List(sampleIncomeStatement),
    balanceSheets = List(sampleBalanceSheet),
    cashFlowStatements = List(sampleCashFlowStatement)
  )

  test("calculates return on assets from net income and total assets") {
    val roa = sampleFinancials.returnOnAssets
    assert(roa.isDefined)
    // 94663000000 / 364980000000 ~ 0.2594
    assert(Math.abs(roa.get - 0.2594) < 0.001)
  }

  test("calculates return on equity from net income and equity") {
    val roe = sampleFinancials.returnOnEquity
    assert(roe.isDefined)
    // 94663000000 / 56950000000 ~ 1.662
    assert(Math.abs(roe.get - 1.662) < 0.001)
  }

  test("calculates asset turnover from revenue and total assets") {
    val turnover = sampleFinancials.assetTurnover
    assert(turnover.isDefined)
    // 383285000000 / 364980000000 ~ 1.050
    assert(Math.abs(turnover.get - 1.050) < 0.001)
  }

  test("calculates interest coverage from EBIT and interest expense") {
    val coverage = sampleFinancials.interestCoverage
    assert(coverage.isDefined)
    // 113033000000 / abs(3000000000) ~ 37.68
    assert(Math.abs(coverage.get - 37.68) < 0.01)
  }

  test("returns no ROA when total assets is zero") {
    val modified = sampleFinancials.copy(
      balanceSheets = List(sampleBalanceSheet.copy(totalAssets = Some(0.0)))
    )
    assertEquals(modified.returnOnAssets, None)
  }

  test("returns no ROA when total assets is absent") {
    val modified = sampleFinancials.copy(
      balanceSheets = List(sampleBalanceSheet.copy(totalAssets = None))
    )
    assertEquals(modified.returnOnAssets, None)
  }

  test("returns no ROE when stockholders equity is zero") {
    val modified = sampleFinancials.copy(
      balanceSheets = List(sampleBalanceSheet.copy(stockholdersEquity = Some(0.0)))
    )
    assertEquals(modified.returnOnEquity, None)
  }

  test("returns no interest coverage when interest expense is zero") {
    val modified = sampleFinancials.copy(
      incomeStatements = List(sampleIncomeStatement.copy(interestExpense = Some(0.0)))
    )
    assertEquals(modified.interestCoverage, None)
  }

  test("returns no interest coverage when interest expense is absent") {
    val modified = sampleFinancials.copy(
      incomeStatements = List(sampleIncomeStatement.copy(interestExpense = None))
    )
    assertEquals(modified.interestCoverage, None)
  }

  test("is non-empty when any statements exist") {
    assert(sampleFinancials.nonEmpty)
  }

  test("is empty when all statement lists are empty") {
    val empty = FinancialStatements.empty(Ticker("TEST"))
    assert(!empty.nonEmpty)
  }

  test("reports empty when no statements exist") {
    val empty = FinancialStatements.empty(Ticker("TEST"))
    assert(empty.isEmpty)
  }

  test("returns most recent income statement") {
    assertEquals(sampleFinancials.latestIncomeStatement, Some(sampleIncomeStatement))
  }

  test("returns no latest income statement when list is empty") {
    val empty = sampleFinancials.copy(incomeStatements = List.empty)
    assertEquals(empty.latestIncomeStatement, None)
  }

  test("period count equals maximum across all statement types") {
    val multi = sampleFinancials.copy(
      incomeStatements =
        List(sampleIncomeStatement, sampleIncomeStatement.copy(reportDate = LocalDate.of(2023, 9, 28))),
      balanceSheets = List(sampleBalanceSheet),
      cashFlowStatements = List(sampleCashFlowStatement, sampleCashFlowStatement, sampleCashFlowStatement)
    )
    assertEquals(multi.periodCount, 3)
  }
}
