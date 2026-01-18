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

  test("returnOnAssets should calculate correctly") {
    val roa = sampleFinancials.returnOnAssets
    assert(roa.isDefined)
    // 94663000000 / 364980000000 ~ 0.2594
    assert(Math.abs(roa.get - 0.2594) < 0.001)
  }

  test("returnOnEquity should calculate correctly") {
    val roe = sampleFinancials.returnOnEquity
    assert(roe.isDefined)
    // 94663000000 / 56950000000 ~ 1.662
    assert(Math.abs(roe.get - 1.662) < 0.001)
  }

  test("assetTurnover should calculate correctly") {
    val turnover = sampleFinancials.assetTurnover
    assert(turnover.isDefined)
    // 383285000000 / 364980000000 ~ 1.050
    assert(Math.abs(turnover.get - 1.050) < 0.001)
  }

  test("interestCoverage should calculate correctly") {
    val coverage = sampleFinancials.interestCoverage
    assert(coverage.isDefined)
    // 113033000000 / abs(3000000000) ~ 37.68
    assert(Math.abs(coverage.get - 37.68) < 0.01)
  }

  test("returnOnAssets should return None when totalAssets is zero") {
    val modified = sampleFinancials.copy(
      balanceSheets = List(sampleBalanceSheet.copy(totalAssets = Some(0.0)))
    )
    assertEquals(modified.returnOnAssets, None)
  }

  test("returnOnAssets should return None when totalAssets is missing") {
    val modified = sampleFinancials.copy(
      balanceSheets = List(sampleBalanceSheet.copy(totalAssets = None))
    )
    assertEquals(modified.returnOnAssets, None)
  }

  test("returnOnEquity should return None when stockholdersEquity is zero") {
    val modified = sampleFinancials.copy(
      balanceSheets = List(sampleBalanceSheet.copy(stockholdersEquity = Some(0.0)))
    )
    assertEquals(modified.returnOnEquity, None)
  }

  test("interestCoverage should return None when interestExpense is zero") {
    val modified = sampleFinancials.copy(
      incomeStatements = List(sampleIncomeStatement.copy(interestExpense = Some(0.0)))
    )
    assertEquals(modified.interestCoverage, None)
  }

  test("interestCoverage should return None when interestExpense is missing") {
    val modified = sampleFinancials.copy(
      incomeStatements = List(sampleIncomeStatement.copy(interestExpense = None))
    )
    assertEquals(modified.interestCoverage, None)
  }

  test("nonEmpty should return true when any statements exist") {
    assert(sampleFinancials.nonEmpty)
  }

  test("nonEmpty should return false when all statements are empty") {
    val empty = FinancialStatements.empty(Ticker("TEST"))
    assert(!empty.nonEmpty)
  }

  test("isEmpty should return true when no statements exist") {
    val empty = FinancialStatements.empty(Ticker("TEST"))
    assert(empty.isEmpty)
  }

  test("latestIncomeStatement should return the first statement") {
    assertEquals(sampleFinancials.latestIncomeStatement, Some(sampleIncomeStatement))
  }

  test("latestIncomeStatement should return None when no statements exist") {
    val empty = sampleFinancials.copy(incomeStatements = List.empty)
    assertEquals(empty.latestIncomeStatement, None)
  }

  test("periodCount should return the maximum of all statement counts") {
    val multi = sampleFinancials.copy(
      incomeStatements =
        List(sampleIncomeStatement, sampleIncomeStatement.copy(reportDate = LocalDate.of(2023, 9, 28))),
      balanceSheets = List(sampleBalanceSheet),
      cashFlowStatements = List(sampleCashFlowStatement, sampleCashFlowStatement, sampleCashFlowStatement)
    )
    assertEquals(multi.periodCount, 3)
  }
}
