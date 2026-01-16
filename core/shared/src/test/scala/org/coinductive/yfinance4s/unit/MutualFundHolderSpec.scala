package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.MutualFundHolder

import java.time.LocalDate

class MutualFundHolderSpec extends FunSuite {

  test("percentHeldFormatted should convert decimal to percentage") {
    val holder = MutualFundHolder(
      organization = "Vanguard Total Stock Market Index Fund",
      reportDate = LocalDate.of(2024, 9, 30),
      percentHeld = 0.0312,
      sharesHeld = 500000L,
      marketValue = 75000000L
    )

    assert(Math.abs(holder.percentHeldFormatted - 3.12) < 0.001)
  }

  test("averageCostPerShare should calculate correctly") {
    val holder = MutualFundHolder(
      organization = "Fidelity Growth Fund",
      reportDate = LocalDate.of(2024, 9, 30),
      percentHeld = 0.02,
      sharesHeld = 500000L,
      marketValue = 100000000L
    )

    assertEquals(holder.averageCostPerShare, Some(200.0))
  }

  test("averageCostPerShare should return None for zero shares") {
    val holder = MutualFundHolder(
      organization = "Empty Fund",
      reportDate = LocalDate.of(2024, 9, 30),
      percentHeld = 0.0,
      sharesHeld = 0L,
      marketValue = 0L
    )

    assertEquals(holder.averageCostPerShare, None)
  }

  test("ordering should sort by percentage descending") {
    val holder1 = MutualFundHolder("Fund A", LocalDate.now(), 0.02, 100, 1000)
    val holder2 = MutualFundHolder("Fund B", LocalDate.now(), 0.05, 200, 2000)
    val holder3 = MutualFundHolder("Fund C", LocalDate.now(), 0.01, 50, 500)

    val sorted = List(holder1, holder2, holder3).sorted

    assertEquals(sorted.map(_.organization), List("Fund B", "Fund A", "Fund C"))
  }
}
