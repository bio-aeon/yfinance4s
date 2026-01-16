package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.IncomeStatement

import java.time.LocalDate

class IncomeStatementSpec extends FunSuite {

  private val sampleStatement = IncomeStatement(
    reportDate = LocalDate.of(2024, 9, 28),
    periodType = "12M",
    currencyCode = "USD",
    totalRevenue = Some(383285000000.0),
    operatingRevenue = Some(383285000000.0),
    costOfRevenue = Some(214137000000.0),
    grossProfit = Some(169148000000.0),
    operatingExpense = Some(58712000000.0),
    sellingGeneralAndAdministration = Some(24873000000.0),
    researchAndDevelopment = Some(33839000000.0),
    depreciationAndAmortization = Some(11445000000.0),
    operatingIncome = Some(110436000000.0),
    interestIncome = Some(3999000000.0),
    interestExpense = Some(3000000000.0),
    netInterestIncome = Some(999000000.0),
    otherIncomeExpense = Some(-422000000.0),
    pretaxIncome = Some(113033000000.0),
    taxProvision = Some(18370000000.0),
    netIncome = Some(94663000000.0),
    netIncomeContinuousOperations = Some(94663000000.0),
    netIncomeCommonStockholders = Some(94663000000.0),
    basicEps = Some(6.11),
    dilutedEps = Some(6.08),
    basicAverageShares = Some(15494000000.0),
    dilutedAverageShares = Some(15558000000.0),
    ebit = Some(113033000000.0),
    ebitda = Some(124478000000.0)
  )

  test("grossMargin should calculate correctly") {
    val margin = sampleStatement.grossMargin
    assert(margin.isDefined)
    // 169148000000 / 383285000000 ~ 0.4413
    assert(Math.abs(margin.get - 0.4413) < 0.001)
  }

  test("operatingMargin should calculate correctly") {
    val margin = sampleStatement.operatingMargin
    assert(margin.isDefined)
    // 110436000000 / 383285000000 ~ 0.2881
    assert(Math.abs(margin.get - 0.2881) < 0.001)
  }

  test("netMargin should calculate correctly") {
    val margin = sampleStatement.netMargin
    assert(margin.isDefined)
    // 94663000000 / 383285000000 ~ 0.2470
    assert(Math.abs(margin.get - 0.2470) < 0.001)
  }

  test("ebitdaMargin should calculate correctly") {
    val margin = sampleStatement.ebitdaMargin
    assert(margin.isDefined)
    // 124478000000 / 383285000000 ~ 0.3248
    assert(Math.abs(margin.get - 0.3248) < 0.001)
  }

  test("effectiveTaxRate should calculate correctly") {
    val rate = sampleStatement.effectiveTaxRate
    assert(rate.isDefined)
    // 18370000000 / 113033000000 ~ 0.1625
    assert(Math.abs(rate.get - 0.1625) < 0.001)
  }

  test("grossMargin should return None when revenue is zero") {
    val stmt = sampleStatement.copy(totalRevenue = Some(0.0))
    assertEquals(stmt.grossMargin, None)
  }

  test("grossMargin should return None when grossProfit is missing") {
    val stmt = sampleStatement.copy(grossProfit = None)
    assertEquals(stmt.grossMargin, None)
  }

  test("grossMargin should return None when totalRevenue is missing") {
    val stmt = sampleStatement.copy(totalRevenue = None)
    assertEquals(stmt.grossMargin, None)
  }

  test("effectiveTaxRate should return None when pretaxIncome is zero") {
    val stmt = sampleStatement.copy(pretaxIncome = Some(0.0))
    assertEquals(stmt.effectiveTaxRate, None)
  }

  test("income statements should sort by date descending") {
    val older = sampleStatement.copy(reportDate = LocalDate.of(2023, 9, 28))
    val newer = sampleStatement.copy(reportDate = LocalDate.of(2024, 9, 28))
    val sorted = List(older, newer).sorted
    assertEquals(sorted.head.reportDate, LocalDate.of(2024, 9, 28))
  }
}
