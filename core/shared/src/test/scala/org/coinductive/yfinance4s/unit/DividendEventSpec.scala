package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.DividendEvent
import org.coinductive.yfinance4s.models.YFinanceQueryResult.DividendEventRaw

import java.time.{ZoneOffset, ZonedDateTime}

class DividendEventSpec extends FunSuite {

  test("fromRaw should convert timestamp to ZonedDateTime correctly") {
    val raw = DividendEventRaw(amount = 0.24, date = 1704067200L)
    val event = DividendEvent.fromRaw("1704067200", raw)

    assertEquals(event.amount, 0.24)
    assertEquals(event.exDate.getYear, 2024)
    assertEquals(event.exDate.getMonthValue, 1)
    assertEquals(event.exDate.getDayOfMonth, 1)
    assertEquals(event.exDate.getZone, ZoneOffset.UTC)
  }

  test("yieldAt should calculate dividend yield correctly") {
    val event = DividendEvent(
      exDate = ZonedDateTime.now(),
      amount = 0.24
    )

    // $0.24 dividend on $100 stock = 0.24% yield
    val yieldValue = event.yieldAt(100.0)
    assert(Math.abs(yieldValue - 0.0024) < 0.0001)
  }

  test("yieldAt should return 0 for zero share price") {
    val event = DividendEvent(
      exDate = ZonedDateTime.now(),
      amount = 0.24
    )

    assertEquals(event.yieldAt(0.0), 0.0)
  }

  test("yieldAt should return 0 for negative share price") {
    val event = DividendEvent(
      exDate = ZonedDateTime.now(),
      amount = 0.24
    )

    assertEquals(event.yieldAt(-10.0), 0.0)
  }

  test("ordering should sort by exDate chronologically") {
    val event1 = DividendEvent(
      exDate = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
      amount = 0.20
    )
    val event2 = DividendEvent(
      exDate = ZonedDateTime.of(2024, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC),
      amount = 0.22
    )
    val event3 = DividendEvent(
      exDate = ZonedDateTime.of(2024, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC),
      amount = 0.24
    )

    val unsorted = List(event2, event3, event1)
    val sorted = unsorted.sorted

    assertEquals(sorted, List(event1, event2, event3))
  }
}
