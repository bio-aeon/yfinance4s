package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.*

import java.time.ZonedDateTime
import scala.concurrent.duration.*

class MarketStatusSpec extends FunSuite {

  // --- MarketTimezone ---

  test("converts a westward millisecond offset to a negative duration") {
    val tz = MarketTimezone(short = "EDT", gmtOffsetMillis = -14400000L)
    assertEquals(tz.gmtOffset, -4.hours)
  }

  test("converts an eastward millisecond offset to a positive duration") {
    val tz = MarketTimezone(short = "JST", gmtOffsetMillis = 9L * 60 * 60 * 1000)
    assertEquals(tz.gmtOffset, 9.hours)
  }

  // --- MarketStatus ---

  private val region = MarketRegion("US")
  private val tz = MarketTimezone(short = "EDT", gmtOffsetMillis = -14400000L)
  private val open = ZonedDateTime.parse("2024-06-27T09:30:00-04:00")
  private val close = ZonedDateTime.parse("2024-06-27T16:00:00-04:00")

  private val openStatus = MarketStatus(region, status = "open", open = open, close = close, timezone = tz)

  test("treats a lowercase 'open' status as an open market") {
    assert(openStatus.isOpen)
  }

  test("treats 'open' status case-insensitively when deciding open market") {
    assert(openStatus.copy(status = "Open").isOpen)
    assert(openStatus.copy(status = "OPEN").isOpen)
  }

  test("treats closed and pre/post-market statuses as a non-open market") {
    assert(!openStatus.copy(status = "closed").isOpen)
    assert(!openStatus.copy(status = "pre-market").isOpen)
    assert(!openStatus.copy(status = "post-market").isOpen)
  }

  test("treats closed, pre-market, post-market, pre, and post statuses as a closed market") {
    assert(openStatus.copy(status = "closed").isClosed)
    assert(openStatus.copy(status = "pre-market").isClosed)
    assert(openStatus.copy(status = "post-market").isClosed)
    assert(openStatus.copy(status = "pre").isClosed)
    assert(openStatus.copy(status = "post").isClosed)
  }

  test("treats status case-insensitively when deciding closed market") {
    assert(openStatus.copy(status = "CLOSED").isClosed)
    assert(openStatus.copy(status = "Pre-Market").isClosed)
  }

  test("treats an open market as not closed") {
    assert(!openStatus.isClosed)
  }

  test("computes the session duration between open and close") {
    assertEquals(openStatus.sessionDuration, 6.hours + 30.minutes)
  }

  test("reports a zero session duration when open equals close") {
    val zero = openStatus.copy(close = open)
    assertEquals(zero.sessionDuration, 0.millis)
  }
}
