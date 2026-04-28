package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.*

import java.time.{ZoneOffset, ZonedDateTime}

class EarningsEventSpec extends FunSuite {

  private val reference: ZonedDateTime = ZonedDateTime.of(2025, 10, 30, 12, 0, 0, 0, ZoneOffset.UTC)

  private def event(
      symbol: String = "AAPL",
      startDateTime: ZonedDateTime = reference,
      timing: EarningsTiming = EarningsTiming.AfterMarketClose,
      marketCap: Option[Long] = Some(3_500_000_000_000L),
      surprisePercent: Option[Double] = None
  ): EarningsEvent =
    EarningsEvent(
      symbol = Ticker(symbol),
      companyName = Some("Apple Inc"),
      marketCap = marketCap,
      eventName = Some("Apple Inc Q4 2025 Earnings Call"),
      startDateTime = startDateTime,
      timing = timing,
      epsEstimate = None,
      epsActual = None,
      surprisePercent = surprisePercent
    )

  // --- Scheduling classification ---

  test("classifies an event after the reference as future") {
    val e = event(startDateTime = reference.plusHours(1))
    assert(e.isFuture(reference))
    assert(!e.isPast(reference))
  }

  test("classifies an event before the reference as past") {
    val e = event(startDateTime = reference.minusHours(1))
    assert(e.isPast(reference))
    assert(!e.isFuture(reference))
  }

  test("treats an event exactly at the reference as neither future nor past") {
    val e = event(startDateTime = reference)
    assert(!e.isFuture(reference))
    assert(!e.isPast(reference))
  }

  // --- Timing classification ---

  test("identifies a BMO release as before market open and not after close") {
    val e = event(timing = EarningsTiming.BeforeMarketOpen)
    assert(e.isBeforeMarketOpen)
    assert(!e.isAfterMarketClose)
  }

  test("identifies an AMC release as after market close and not before open") {
    val e = event(timing = EarningsTiming.AfterMarketClose)
    assert(e.isAfterMarketClose)
    assert(!e.isBeforeMarketOpen)
  }

  test("treats an unsupplied timing as neither before open nor after close") {
    val e = event(timing = EarningsTiming.TimeNotSupplied)
    assert(!e.isBeforeMarketOpen)
    assert(!e.isAfterMarketClose)
  }

  // --- Surprise classification ---

  test("recognizes a positive surprise as a beat") {
    val e = event(surprisePercent = Some(2.5))
    assertEquals(e.isBeat, Some(true))
    assertEquals(e.isMiss, Some(false))
  }

  test("recognizes a negative surprise as a miss") {
    val e = event(surprisePercent = Some(-1.5))
    assertEquals(e.isMiss, Some(true))
    assertEquals(e.isBeat, Some(false))
  }

  test("treats a zero surprise as neither beat nor miss") {
    val e = event(surprisePercent = Some(0.0))
    assertEquals(e.isBeat, Some(false))
    assertEquals(e.isMiss, Some(false))
  }

  // --- Orderings ---

  test("sorts events by scheduled time ascending") {
    val a = event(symbol = "A", startDateTime = reference.plusDays(2))
    val b = event(symbol = "B", startDateTime = reference.plusDays(1))
    val c = event(symbol = "C", startDateTime = reference)
    val sorted = List(a, b, c).sorted(using EarningsEvent.byDateAsc)
    assertEquals(sorted.map(_.symbol.value), List("C", "B", "A"))
  }

  test("sorts events by market cap descending with missing caps last") {
    val a = event(symbol = "A", marketCap = None)
    val b = event(symbol = "B", marketCap = Some(500_000_000_000L))
    val c = event(symbol = "C", marketCap = Some(3_000_000_000_000L))
    val sorted = List(a, b, c).sorted(using EarningsEvent.byMarketCapDesc)
    assertEquals(sorted.map(_.symbol.value), List("C", "B", "A"))
  }
}
