package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.SplitEvent
import org.coinductive.yfinance4s.models.internal.SplitEventRaw

import java.time.{ZoneOffset, ZonedDateTime}

class SplitEventSpec extends FunSuite {

  test("parses epoch timestamp into date") {
    val raw = SplitEventRaw(
      date = 1598832000L,
      numerator = 4,
      denominator = 1,
      splitRatio = "4:1"
    )
    val event = SplitEvent.fromRaw("1598832000", raw)

    assertEquals(event.numerator, 4)
    assertEquals(event.denominator, 1)
    assertEquals(event.splitRatio, "4:1")
    assertEquals(event.exDate.getYear, 2020)
    assertEquals(event.exDate.getMonthValue, 8)
  }

  test("calculates forward split factor (4:1 = 4.0)") {
    val forward4to1 = SplitEvent(
      exDate = ZonedDateTime.now(),
      numerator = 4,
      denominator = 1,
      splitRatio = "4:1"
    )
    assertEquals(forward4to1.factor, 4.0)
  }

  test("calculates reverse split factor (1:10 = 0.1)") {
    val reverse1to10 = SplitEvent(
      exDate = ZonedDateTime.now(),
      numerator = 1,
      denominator = 10,
      splitRatio = "1:10"
    )
    assertEquals(reverse1to10.factor, 0.1)
  }

  test("identifies forward splits (numerator > denominator)") {
    val forwardSplit = SplitEvent(
      exDate = ZonedDateTime.now(),
      numerator = 4,
      denominator = 1,
      splitRatio = "4:1"
    )
    assert(forwardSplit.isForwardSplit)
    assert(!forwardSplit.isReverseSplit)
  }

  test("identifies reverse splits (numerator < denominator)") {
    val reverseSplit = SplitEvent(
      exDate = ZonedDateTime.now(),
      numerator = 1,
      denominator = 10,
      splitRatio = "1:10"
    )
    assert(reverseSplit.isReverseSplit)
    assert(!reverseSplit.isForwardSplit)
  }

  test("1:1 ratio is neither forward nor reverse") {
    val noChange = SplitEvent(
      exDate = ZonedDateTime.now(),
      numerator = 1,
      denominator = 1,
      splitRatio = "1:1"
    )
    assert(!noChange.isForwardSplit)
    assert(!noChange.isReverseSplit)
    assertEquals(noChange.factor, 1.0)
  }

  test("sorts by ex-date chronologically") {
    val event1 = SplitEvent(
      exDate = ZonedDateTime.of(2014, 6, 9, 0, 0, 0, 0, ZoneOffset.UTC),
      numerator = 7,
      denominator = 1,
      splitRatio = "7:1"
    )
    val event2 = SplitEvent(
      exDate = ZonedDateTime.of(2020, 8, 31, 0, 0, 0, 0, ZoneOffset.UTC),
      numerator = 4,
      denominator = 1,
      splitRatio = "4:1"
    )

    val unsorted = List(event2, event1)
    val sorted = unsorted.sorted

    assertEquals(sorted, List(event1, event2))
  }
}
