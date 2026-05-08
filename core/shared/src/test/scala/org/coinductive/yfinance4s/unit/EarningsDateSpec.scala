package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.*

import java.time.{ZoneOffset, ZonedDateTime}

class EarningsDateSpec extends FunSuite {

  private val reference: ZonedDateTime = ZonedDateTime.of(2025, 10, 30, 12, 0, 0, 0, ZoneOffset.UTC)

  private def date(
      d: ZonedDateTime = reference,
      eventType: EarningsEventType = EarningsEventType.EarningsReport,
      surprisePercent: Option[Double] = None
  ): EarningsDate =
    EarningsDate(
      date = d,
      eventType = eventType,
      epsEstimate = None,
      epsActual = None,
      surprisePercent = surprisePercent,
      timezoneShort = Some("EDT")
    )

  // --- Event-type classification ---

  test("an earnings call event is recognized only as a call") {
    val d = date(eventType = EarningsEventType.EarningsCall)
    assert(d.isCall)
    assert(!d.isReport)
    assert(!d.isMeeting)
  }

  test("an earnings report event is recognized only as a report") {
    val d = date(eventType = EarningsEventType.EarningsReport)
    assert(d.isReport)
    assert(!d.isCall)
    assert(!d.isMeeting)
  }

  test("a stockholders meeting event is recognized only as a meeting") {
    val d = date(eventType = EarningsEventType.StockholdersMeeting)
    assert(d.isMeeting)
    assert(!d.isCall)
    assert(!d.isReport)
  }

  // --- Scheduling classification ---

  test("classifies a date after the reference as future and not past") {
    val d = date(d = reference.plusHours(1))
    assert(d.isFuture(reference))
    assert(!d.isPast(reference))
  }

  test("treats a date exactly at the reference as neither future nor past") {
    val d = date(d = reference)
    assert(!d.isFuture(reference))
    assert(!d.isPast(reference))
  }

  // --- Surprise classification ---

  test("recognizes signed surprises as beat / miss") {
    assertEquals(date(surprisePercent = Some(2.5)).isBeat, Some(true))
    assertEquals(date(surprisePercent = Some(-1.5)).isMiss, Some(true))
  }

  test("treats zero surprise as neither beat nor miss") {
    assertEquals(date(surprisePercent = Some(0.0)).isBeat, Some(false))
    assertEquals(date(surprisePercent = Some(0.0)).isMiss, Some(false))
  }

  // --- Ordering ---

  test("sorts dates descending (most recent first) under the default-sort ordering") {
    val a = date(d = reference.minusDays(1))
    val b = date(d = reference.plusDays(1))
    val c = date(d = reference)
    val sorted = List(a, b, c).sorted(using EarningsDate.byDateDesc)
    assertEquals(sorted.map(_.date), List(b.date, c.date, a.date))
  }
}
