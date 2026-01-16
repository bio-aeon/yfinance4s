package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.BalanceSheet

import java.time.LocalDate

class BalanceSheetSpec extends FunSuite {

  private val sampleSheet = BalanceSheet(
    reportDate = LocalDate.of(2024, 9, 28),
    periodType = "12M",
    currencyCode = "USD",
    totalAssets = Some(364980000000.0),
    currentAssets = Some(152987000000.0),
    cashAndCashEquivalents = Some(29943000000.0),
    shortTermInvestments = Some(35228000000.0),
    accountsReceivable = Some(66243000000.0),
    inventory = Some(7286000000.0),
    otherCurrentAssets = Some(14287000000.0),
    totalNonCurrentAssets = Some(211993000000.0),
    netPpe = Some(45680000000.0),
    grossPpe = Some(119681000000.0),
    accumulatedDepreciation = Some(-74001000000.0),
    goodwill = None,
    otherIntangibleAssets = None,
    longTermInvestments = Some(100544000000.0),
    otherNonCurrentAssets = Some(65769000000.0),
    currentLiabilities = Some(176392000000.0),
    accountsPayable = Some(68960000000.0),
    currentDebt = Some(20904000000.0),
    otherCurrentLiabilities = Some(86528000000.0),
    totalNonCurrentLiabilities = Some(89140000000.0),
    longTermDebt = Some(85750000000.0),
    otherNonCurrentLiabilities = Some(3390000000.0),
    totalLiabilities = Some(265532000000.0),
    totalDebt = Some(106654000000.0),
    netDebt = Some(76711000000.0),
    stockholdersEquity = Some(56950000000.0),
    commonStock = Some(79850000000.0),
    additionalPaidInCapital = None,
    retainedEarnings = Some(-22900000000.0),
    treasuryStock = None,
    sharesIssued = Some(15550000000.0),
    ordinarySharesNumber = Some(15550000000.0),
    workingCapital = Some(-23405000000.0),
    tangibleBookValue = Some(56950000000.0),
    investedCapital = Some(163604000000.0)
  )

  test("currentRatio should calculate correctly") {
    val ratio = sampleSheet.currentRatio
    assert(ratio.isDefined)
    // 152987000000 / 176392000000 ~ 0.8673
    assert(Math.abs(ratio.get - 0.8673) < 0.001)
  }

  test("quickRatio should calculate correctly") {
    val ratio = sampleSheet.quickRatio
    assert(ratio.isDefined)
    // (152987000000 - 7286000000) / 176392000000 ~ 0.8260
    assert(Math.abs(ratio.get - 0.8260) < 0.001)
  }

  test("quickRatio should handle missing inventory") {
    val sheet = sampleSheet.copy(inventory = None)
    val ratio = sheet.quickRatio
    assert(ratio.isDefined)
    // Should treat missing inventory as 0
    // 152987000000 / 176392000000 ~ 0.8673
    assert(Math.abs(ratio.get - 0.8673) < 0.001)
  }

  test("debtToEquity should calculate correctly") {
    val ratio = sampleSheet.debtToEquity
    assert(ratio.isDefined)
    // 106654000000 / 56950000000 ~ 1.8727
    assert(Math.abs(ratio.get - 1.8727) < 0.001)
  }

  test("debtToAssets should calculate correctly") {
    val ratio = sampleSheet.debtToAssets
    assert(ratio.isDefined)
    // 106654000000 / 364980000000 ~ 0.2922
    assert(Math.abs(ratio.get - 0.2922) < 0.001)
  }

  test("bookValuePerShare should calculate correctly") {
    val bvps = sampleSheet.bookValuePerShare
    assert(bvps.isDefined)
    // 56950000000 / 15550000000 ~ 3.66
    assert(Math.abs(bvps.get - 3.66) < 0.01)
  }

  test("tangibleBookValuePerShare should calculate correctly") {
    val tbvps = sampleSheet.tangibleBookValuePerShare
    assert(tbvps.isDefined)
    // 56950000000 / 15550000000 ~ 3.66
    assert(Math.abs(tbvps.get - 3.66) < 0.01)
  }

  test("currentRatio should return None when currentLiabilities is zero") {
    val sheet = sampleSheet.copy(currentLiabilities = Some(0.0))
    assertEquals(sheet.currentRatio, None)
  }

  test("currentRatio should return None when currentLiabilities is missing") {
    val sheet = sampleSheet.copy(currentLiabilities = None)
    assertEquals(sheet.currentRatio, None)
  }

  test("debtToEquity should return None when stockholdersEquity is zero") {
    val sheet = sampleSheet.copy(stockholdersEquity = Some(0.0))
    assertEquals(sheet.debtToEquity, None)
  }

  test("bookValuePerShare should return None when shares is zero") {
    val sheet = sampleSheet.copy(ordinarySharesNumber = Some(0.0))
    assertEquals(sheet.bookValuePerShare, None)
  }

  test("balance sheets should sort by date descending") {
    val older = sampleSheet.copy(reportDate = LocalDate.of(2023, 9, 28))
    val newer = sampleSheet.copy(reportDate = LocalDate.of(2024, 9, 28))
    val sorted = List(older, newer).sorted
    assertEquals(sorted.head.reportDate, LocalDate.of(2024, 9, 28))
  }
}
