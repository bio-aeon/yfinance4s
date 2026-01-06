package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.{CorporateActions, DividendEvent, SplitEvent}

import java.time.{ZoneOffset, ZonedDateTime}

class CorporateActionsSpec extends FunSuite {

  test("empty should have no dividends or splits") {
    val empty = CorporateActions.empty
    assert(empty.isEmpty)
    assert(!empty.nonEmpty)
    assertEquals(empty.dividends, List.empty)
    assertEquals(empty.splits, List.empty)
  }

  test("isEmpty should return true when both lists are empty") {
    val actions = CorporateActions(List.empty, List.empty)
    assert(actions.isEmpty)
    assert(!actions.nonEmpty)
  }

  test("isEmpty should return false when dividends exist") {
    val dividend = DividendEvent(
      exDate = ZonedDateTime.now(),
      amount = 0.24
    )
    val actions = CorporateActions(List(dividend), List.empty)
    assert(!actions.isEmpty)
    assert(actions.nonEmpty)
  }

  test("isEmpty should return false when splits exist") {
    val split = SplitEvent(
      exDate = ZonedDateTime.now(),
      numerator = 4,
      denominator = 1,
      splitRatio = "4:1"
    )
    val actions = CorporateActions(List.empty, List(split))
    assert(!actions.isEmpty)
    assert(actions.nonEmpty)
  }

  test("totalDividendAmount should sum all dividend amounts") {
    val dividends = List(
      DividendEvent(ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), 0.24),
      DividendEvent(ZonedDateTime.of(2024, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC), 0.24),
      DividendEvent(ZonedDateTime.of(2024, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC), 0.25),
      DividendEvent(ZonedDateTime.of(2024, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC), 0.25)
    )
    val actions = CorporateActions(dividends, List.empty)

    assert(Math.abs(actions.totalDividendAmount - 0.98) < 0.001)
  }

  test("totalDividendAmount should return 0 when no dividends") {
    val actions = CorporateActions(List.empty, List.empty)
    assertEquals(actions.totalDividendAmount, 0.0)
  }

  test("cumulativeSplitFactor should return 1.0 when no splits") {
    val actions = CorporateActions(List.empty, List.empty)
    assertEquals(actions.cumulativeSplitFactor, 1.0)
  }

  test("cumulativeSplitFactor should return single split factor") {
    val split = SplitEvent(
      exDate = ZonedDateTime.now(),
      numerator = 4,
      denominator = 1,
      splitRatio = "4:1"
    )
    val actions = CorporateActions(List.empty, List(split))
    assertEquals(actions.cumulativeSplitFactor, 4.0)
  }

  test("cumulativeSplitFactor should multiply multiple split factors") {
    val splits = List(
      SplitEvent(ZonedDateTime.of(2014, 6, 9, 0, 0, 0, 0, ZoneOffset.UTC), 7, 1, "7:1"),
      SplitEvent(ZonedDateTime.of(2020, 8, 31, 0, 0, 0, 0, ZoneOffset.UTC), 4, 1, "4:1")
    )
    val actions = CorporateActions(List.empty, splits)
    assertEquals(actions.cumulativeSplitFactor, 28.0)
  }

  test("dividendCount should return correct count") {
    val dividends = List(
      DividendEvent(ZonedDateTime.now(), 0.24),
      DividendEvent(ZonedDateTime.now(), 0.24),
      DividendEvent(ZonedDateTime.now(), 0.24)
    )
    val actions = CorporateActions(dividends, List.empty)
    assertEquals(actions.dividendCount, 3)
  }

  test("splitCount should return correct count") {
    val splits = List(
      SplitEvent(ZonedDateTime.now(), 7, 1, "7:1"),
      SplitEvent(ZonedDateTime.now(), 4, 1, "4:1")
    )
    val actions = CorporateActions(List.empty, splits)
    assertEquals(actions.splitCount, 2)
  }
}
