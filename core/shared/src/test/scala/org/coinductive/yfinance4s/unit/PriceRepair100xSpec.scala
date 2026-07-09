package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.PriceRepair
import org.coinductive.yfinance4s.PriceRepair.{Bar, Context}
import org.coinductive.yfinance4s.models.Interval

import java.time.{ZoneOffset, ZonedDateTime}

class PriceRepair100xSpec extends FunSuite {

  private val start = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
  private val ctx = Context(currency = "USD", interval = Interval.`1Day`, isFx = false)

  private def bar(i: Int, open: Double, high: Double, low: Double, close: Double, adjClose: Double): Bar =
    Bar(
      datetime = start.plusDays(i.toLong),
      open = open,
      high = high,
      low = low,
      close = close,
      adjClose = adjClose,
      volume = 1000L,
      dividend = 0.0,
      splitRatio = 0.0,
      repaired = false
    )

  private def baseline(i: Int): Bar = bar(i, open = 10.0, high = 10.4, low = 9.6, close = 10.2, adjClose = 10.2)

  private def assertClose(obtained: Double, expected: Double): Unit =
    assert(math.abs(obtained - expected) < 0.001, s"expected ~$expected, got $obtained")

  test("rescales a single 100x-too-large close down by one hundred") {
    val bars = (0 to 5).toVector.map(baseline).updated(3, baseline(3).copy(close = 1020.0))
    val result = PriceRepair.fixUnitRandomMixups(bars, ctx)
    assertClose(result(3).close, 10.2)
    assert(result(3).repaired, "the rescaled bar should carry the repaired flag")
    assertEquals(result(3).open, 10.0)
    assertEquals(result(3).high, 10.4)
    assertEquals(result(3).low, 9.6)
    assertEquals(result.zipWithIndex.filter(_._1.repaired).map(_._2), Vector(3))
  }

  test("rescales a 100x-too-small close up by one hundred") {
    val bars = (0 to 5).toVector.map(baseline).updated(3, baseline(3).copy(close = 0.102))
    val result = PriceRepair.fixUnitRandomMixups(bars, ctx)
    assertClose(result(3).close, 10.2)
    assert(result(3).repaired, "the rescaled bar should carry the repaired flag")
  }

  test("repairs open, close and adj close together in one bar") {
    val centsBar = bar(3, open = 1000.0, high = 1040.0, low = 960.0, close = 1020.0, adjClose = 1020.0)
    val bars = (0 to 5).toVector.map(baseline).updated(3, centsBar)
    val result = PriceRepair.fixUnitRandomMixups(bars, ctx)
    assertClose(result(3).open, 10.0)
    assertClose(result(3).close, 10.2)
    assertClose(result(3).adjClose, 10.2)
    assertClose(result(3).high, 10.2) // crude: max of repaired open/close
    assertClose(result(3).low, 10.0) // crude: min of repaired open/close
    assert(result(3).repaired, "the rescaled bar should carry the repaired flag")
  }

  test("derives repaired high and low from repaired open and close") {
    val highOnly = baseline(3).copy(high = 1040.0)
    val bars = (0 to 5).toVector.map(baseline).updated(3, highOnly)
    val result = PriceRepair.fixUnitRandomMixups(bars, ctx)
    assertClose(result(3).high, 10.2) // max(open = 10.0, close = 10.2), not 1040 * 0.01
    assertEquals(result(3).open, 10.0)
    assertEquals(result(3).close, 10.2)
    assert(result(3).repaired, "the bar with the repaired high should carry the flag")
  }

  test("leaves a legitimate large move untouched") {
    val bars = (0 to 5).toVector.map { i =>
      if (i < 3) baseline(i)
      else bar(i, open = 30.0, high = 31.2, low = 28.8, close = 30.6, adjClose = 30.6)
    }
    assertEquals(PriceRepair.fixUnitRandomMixups(bars, ctx), bars)
  }

  test("does not repair a single-row table") {
    val bars = Vector(baseline(0).copy(close = 1020.0))
    assertEquals(PriceRepair.fixUnitRandomMixups(bars, ctx), bars)
  }

  test("excludes rows containing a zero from 100x detection") {
    val bars = (0 to 5).toVector
      .map(baseline)
      .updated(2, baseline(2).copy(close = 0.0))
      .updated(4, baseline(4).copy(close = 1020.0))
    val result = PriceRepair.fixUnitRandomMixups(bars, ctx)
    assertEquals(result(2), bars(2)) // the zero row is left to the zero path
    assertClose(result(4).close, 10.2)
    assert(result(4).repaired, "the 100x bar next to a zero row should still be repaired")
  }

  test("returns the table unchanged when zeros leave a single usable row") {
    val bars = Vector(baseline(0).copy(close = 0.0), baseline(1).copy(close = 1020.0))
    assertEquals(PriceRepair.fixUnitRandomMixups(bars, ctx), bars)
  }

  test("returns an all-clean table byte-identical") {
    val bars = (0 to 5).toVector.map(baseline)
    assertEquals(PriceRepair.fixUnitRandomMixups(bars, ctx), bars)
  }
}
