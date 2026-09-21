package io.github.coinductive.yfinance4s.unit

import munit.FunSuite
import io.github.coinductive.yfinance4s.models.InstrumentType

class InstrumentTypeSpec extends FunSuite {

  test("resolves known instrument types case-insensitively") {
    assertEquals(InstrumentType.fromString("EQUITY"), InstrumentType.Equity)
    assertEquals(InstrumentType.fromString("etf"), InstrumentType.Etf)
    assertEquals(InstrumentType.fromString("MutualFund"), InstrumentType.MutualFund)
  }

  test("preserves an unrecognised instrument type as Other") {
    assertEquals(InstrumentType.fromString("WARRANT"), InstrumentType.Other("WARRANT"))
  }

  test("classifies ETFs and mutual funds as funds and others not") {
    assert(InstrumentType.Etf.isFund)
    assert(InstrumentType.MutualFund.isFund)
    assert(!InstrumentType.Equity.isFund)
    assert(!InstrumentType.Index.isFund)
  }
}
