package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.InstitutionalHolder

import java.time.LocalDate

class InstitutionalHolderSpec extends FunSuite {

  test("formats decimal holdings as percentage") {
    val holder = InstitutionalHolder(
      organization = "Vanguard",
      reportDate = LocalDate.of(2024, 9, 30),
      percentHeld = 0.0901,
      sharesHeld = 1000000L,
      marketValue = 100000000L
    )

    assert(Math.abs(holder.percentHeldFormatted - 9.01) < 0.001)
  }

  test("calculates average cost per share from market value and shares") {
    val holder = InstitutionalHolder(
      organization = "BlackRock",
      reportDate = LocalDate.of(2024, 9, 30),
      percentHeld = 0.05,
      sharesHeld = 1000000L,
      marketValue = 150000000L
    )

    assertEquals(holder.averageCostPerShare, Some(150.0))
  }

  test("returns no average cost when shares are zero") {
    val holder = InstitutionalHolder(
      organization = "Empty Fund",
      reportDate = LocalDate.of(2024, 9, 30),
      percentHeld = 0.0,
      sharesHeld = 0L,
      marketValue = 0L
    )

    assertEquals(holder.averageCostPerShare, None)
  }

  test("sorts by percentage held descending") {
    val holder1 = InstitutionalHolder("A", LocalDate.now(), 0.05, 100, 1000)
    val holder2 = InstitutionalHolder("B", LocalDate.now(), 0.10, 200, 2000)
    val holder3 = InstitutionalHolder("C", LocalDate.now(), 0.02, 50, 500)

    val sorted = List(holder1, holder2, holder3).sorted

    assertEquals(sorted.map(_.organization), List("B", "A", "C"))
  }

  test("sorts by report date descending") {
    val holder1 = InstitutionalHolder("A", LocalDate.of(2024, 1, 1), 0.05, 100, 1000)
    val holder2 = InstitutionalHolder("B", LocalDate.of(2024, 6, 1), 0.05, 100, 1000)
    val holder3 = InstitutionalHolder("C", LocalDate.of(2024, 3, 1), 0.05, 100, 1000)

    val sorted = List(holder1, holder2, holder3).sorted(using InstitutionalHolder.orderingByDate)

    assertEquals(sorted.map(_.organization), List("B", "C", "A"))
  }
}
