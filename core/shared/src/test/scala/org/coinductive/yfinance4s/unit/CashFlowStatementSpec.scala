package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.CashFlowStatement

import java.time.LocalDate

class CashFlowStatementSpec extends FunSuite {

  private val sampleStatement = CashFlowStatement(
    reportDate = LocalDate.of(2024, 9, 28),
    periodType = "12M",
    currencyCode = "USD",
    operatingCashFlow = Some(118254000000.0),
    netIncomeFromContinuingOperations = Some(94663000000.0),
    depreciationAmortizationDepletion = Some(11445000000.0),
    stockBasedCompensation = Some(11658000000.0),
    deferredIncomeTax = Some(-5050000000.0),
    changeInWorkingCapital = Some(6066000000.0),
    changeInReceivables = Some(-11412000000.0),
    changeInInventory = Some(1037000000.0),
    changeInPayables = Some(15552000000.0),
    investingCashFlow = Some(-2935000000.0),
    capitalExpenditure = Some(-9447000000.0),
    purchaseOfInvestment = Some(-48656000000.0),
    saleOfInvestment = Some(62346000000.0),
    purchaseOfBusiness = Some(-1789000000.0),
    saleOfBusiness = None,
    netPpePurchaseAndSale = Some(-4611000000.0),
    financingCashFlow = Some(-121549000000.0),
    issuanceOfDebt = Some(5000000000.0),
    repaymentOfDebt = Some(-12941000000.0),
    issuanceOfCapitalStock = None,
    repurchaseOfCapitalStock = Some(-94949000000.0),
    cashDividendsPaid = Some(-15234000000.0),
    beginningCashPosition = Some(30737000000.0),
    endCashPosition = Some(29943000000.0),
    changesInCash = Some(-6230000000.0),
    effectOfExchangeRateChanges = Some(436000000.0),
    freeCashFlow = Some(108807000000.0)
  )

  test("calculatedFreeCashFlow should calculate correctly") {
    val fcf = sampleStatement.calculatedFreeCashFlow
    assert(fcf.isDefined)
    // 118254000000 - abs(-9447000000) = 108807000000
    assertEquals(fcf.get, 108807000000.0)
  }

  test("calculatedFreeCashFlow should handle positive capex value") {
    // Some APIs report capex as positive
    val stmt = sampleStatement.copy(capitalExpenditure = Some(9447000000.0))
    val fcf = stmt.calculatedFreeCashFlow
    assert(fcf.isDefined)
    assertEquals(fcf.get, 108807000000.0)
  }

  test("freeCashFlowYield should calculate correctly") {
    val marketCap = 3000000000000.0 // $3 trillion
    val fcfYield = sampleStatement.freeCashFlowYield(marketCap)
    assert(fcfYield.isDefined)
    // 108807000000 / 3000000000000 ~ 0.0363
    assert(Math.abs(fcfYield.get - 0.0363) < 0.001)
  }

  test("freeCashFlowYield should use API freeCashFlow when available") {
    val stmt = sampleStatement.copy(freeCashFlow = Some(100000000000.0))
    val marketCap = 3000000000000.0
    val fcfYield = stmt.freeCashFlowYield(marketCap)
    assert(fcfYield.isDefined)
    // 100000000000 / 3000000000000 ~ 0.0333
    assert(Math.abs(fcfYield.get - 0.0333) < 0.001)
  }

  test("freeCashFlowYield should fall back to calculated when API value missing") {
    val stmt = sampleStatement.copy(freeCashFlow = None)
    val marketCap = 3000000000000.0
    val fcfYield = stmt.freeCashFlowYield(marketCap)
    assert(fcfYield.isDefined)
    // Uses calculatedFreeCashFlow: 108807000000
    assert(Math.abs(fcfYield.get - 0.0363) < 0.001)
  }

  test("cashFlowToNetIncomeRatio should calculate correctly") {
    val ratio = sampleStatement.cashFlowToNetIncomeRatio
    assert(ratio.isDefined)
    // 118254000000 / 94663000000 ~ 1.249
    assert(Math.abs(ratio.get - 1.249) < 0.001)
  }

  test("capexToOperatingCashFlow should calculate correctly") {
    val ratio = sampleStatement.capexToOperatingCashFlow
    assert(ratio.isDefined)
    // abs(-9447000000) / 118254000000 ~ 0.0799
    assert(Math.abs(ratio.get - 0.0799) < 0.001)
  }

  test("netDebtChange should calculate correctly") {
    val change = sampleStatement.netDebtChange
    assert(change.isDefined)
    // abs(-12941000000) - abs(5000000000) = 7941000000
    assertEquals(change.get, 7941000000.0)
  }

  test("netDebtChange should handle missing repayment") {
    val stmt = sampleStatement.copy(repaymentOfDebt = None)
    val change = stmt.netDebtChange
    assert(change.isDefined)
    // 0 - 5000000000 = -5000000000
    assertEquals(change.get, -5000000000.0)
  }

  test("netDebtChange should handle missing issuance") {
    val stmt = sampleStatement.copy(issuanceOfDebt = None)
    val change = stmt.netDebtChange
    assert(change.isDefined)
    // 12941000000 - 0 = 12941000000
    assertEquals(change.get, 12941000000.0)
  }

  test("cashFlowToNetIncomeRatio should return None when netIncome is zero") {
    val stmt = sampleStatement.copy(netIncomeFromContinuingOperations = Some(0.0))
    assertEquals(stmt.cashFlowToNetIncomeRatio, None)
  }

  test("capexToOperatingCashFlow should return None when operating cash flow is zero") {
    val stmt = sampleStatement.copy(operatingCashFlow = Some(0.0))
    assertEquals(stmt.capexToOperatingCashFlow, None)
  }

  test("cash flow statements should sort by date descending") {
    val older = sampleStatement.copy(reportDate = LocalDate.of(2023, 9, 28))
    val newer = sampleStatement.copy(reportDate = LocalDate.of(2024, 9, 28))
    val sorted = List(older, newer).sorted
    assertEquals(sorted.head.reportDate, LocalDate.of(2024, 9, 28))
  }
}
