package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.PriceRepair
import org.coinductive.yfinance4s.PriceRepair.*
import org.coinductive.yfinance4s.models.Interval

import java.time.{ZoneOffset, ZonedDateTime}

class PriceRepairZeroesSpec extends FunSuite {

  private val start = ZonedDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC)
  private val daily = Context(currency = "USD", interval = Interval.`1Day`, isFx = false)
  private val dailyFx = Context(currency = "USD", interval = Interval.`1Day`, isFx = true)
  private val hourly = Context(currency = "USD", interval = Interval.`1Hour`, isFx = false)

  private def bar(
      i: Int,
      open: Double = 10.0,
      high: Double = 10.0,
      low: Double = 10.0,
      close: Double = 10.0,
      adjClose: Double = 10.0,
      volume: Long = 1000L,
      splitRatio: Double = 0.0,
      repaired: Boolean = false,
      hourly: Boolean = false
  ): Bar =
    Bar(
      datetime = if (hourly) start.plusHours(i.toLong) else start.plusDays(i.toLong),
      open = open,
      high = high,
      low = low,
      close = close,
      adjClose = adjClose,
      volume = volume,
      dividend = 0.0,
      splitRatio = splitRatio,
      repaired = repaired
    )

  test("flags a zero close as a bad price cell") {
    val bars = Vector(bar(0), bar(1, close = 0.0), bar(2))
    val tags = PriceRepair.detectZeroes(bars, daily)
    assertEquals(tags.priceCells, Set[(Int, PriceColumn)]((1, PriceColumn.Close)))
    assertEquals(tags.volumeRows, Set.empty[Int])
  }

  test("flags a NaN price cell") {
    val bars = Vector(bar(0), bar(1, open = Double.NaN), bar(2))
    val tags = PriceRepair.detectZeroes(bars, daily)
    assertEquals(tags.priceCells, Set[(Int, PriceColumn)]((1, PriceColumn.Open)))
  }

  test("flags zero volume when the price moved within the bar") {
    val bars = Vector(bar(0), bar(1, high = 12.0, low = 9.0, volume = 0L), bar(2))
    val tags = PriceRepair.detectZeroes(bars, daily)
    assertEquals(tags.volumeRows, Set(1))
    assertEquals(tags.priceCells, Set.empty[(Int, PriceColumn)])
  }

  test("flags zero volume when the price moved on an FX ticker") {
    // The bad price on bar 0 opens the repair-worth guard; the moving zero-volume bar 2 is then
    // tagged by the movement rule, which applies outside the FX guard.
    val bars = Vector(
      bar(0, close = 0.0, volume = 0L),
      bar(1, volume = 0L),
      bar(2, high = 12.0, low = 9.0, volume = 0L)
    )
    val tags = PriceRepair.detectZeroes(bars, dailyFx)
    assert(tags.volumeRows.contains(2), s"expected the moving FX bar's volume to be tagged, got ${tags.volumeRows}")
  }

  test("flags zero volume on a row with a bad price") {
    val bars = Vector(bar(0), bar(1, close = 0.0, volume = 0L), bar(2))
    val tags = PriceRepair.detectZeroes(bars, daily)
    assertEquals(tags.volumeRows, Set(1))
  }

  test("flags an interday zero-volume bar with a >5% close move") {
    val bars = Vector(
      bar(0),
      bar(1, open = 11.0, high = 11.0, low = 11.0, close = 11.0, adjClose = 11.0, volume = 0L),
      bar(2)
    )
    val tags = PriceRepair.detectZeroes(bars, daily)
    assertEquals(tags.volumeRows, Set(1))
    assertEquals(tags.priceCells, Set.empty[(Int, PriceColumn)])
  }

  test("does not apply the close-move volume rule to FX tickers") {
    val bars = Vector(
      bar(0, volume = 0L),
      bar(1, open = 11.0, high = 11.0, low = 11.0, close = 11.0, adjClose = 11.0, volume = 0L),
      bar(2, volume = 0L)
    )
    val tags = PriceRepair.detectZeroes(bars, dailyFx)
    assert(tags.isEmpty, s"expected no tags for an FX close move, got $tags")
  }

  test("forces prices bad on a split day with no movement") {
    val bars = Vector(bar(0), bar(1, splitRatio = 4.0), bar(2))
    val tags = PriceRepair.detectZeroes(bars, daily)
    val expected: Set[(Int, PriceColumn)] = Set(
      (1, PriceColumn.Open),
      (1, PriceColumn.High),
      (1, PriceColumn.Low),
      (1, PriceColumn.Close),
      (1, PriceColumn.AdjClose)
    )
    assertEquals(tags.priceCells, expected)
  }

  test("does not attempt repair when every price cell is bad") {
    val bars = Vector(
      bar(0, open = 0.0, high = 0.0, low = 0.0, close = 0.0, adjClose = 0.0),
      bar(1, open = 0.0, high = 0.0, low = 0.0, close = 0.0, adjClose = 0.0)
    )
    assert(PriceRepair.detectZeroes(bars, daily).isEmpty)
  }

  test("ignores an intraday day that is more than half bad") {
    // Day 1: two of three bars bad (> 50%) - excluded. Day 2: one of three bad - kept.
    val dayOne = Vector(
      bar(0, close = 0.0, hourly = true),
      bar(1, close = 0.0, hourly = true),
      bar(2, hourly = true)
    )
    val dayTwo = Vector(
      bar(24, close = 0.0, hourly = true),
      bar(25, hourly = true),
      bar(26, hourly = true)
    )
    val tags = PriceRepair.detectZeroes(dayOne ++ dayTwo, hourly)
    assertEquals(tags.priceCells, Set[(Int, PriceColumn)]((3, PriceColumn.Close)))
  }

  test("restores originals for tagged cells under the NoOp reconstructor") {
    val bars = Vector(bar(0), bar(1, close = 0.0, volume = 0L), bar(2, high = 12.0, low = 9.0, volume = 0L))
    val tags = PriceRepair.detectZeroes(bars, daily)
    assert(!tags.isEmpty, "fixture should produce tags")
    assertEquals(PriceRepair.applyReconstruction(bars, tags, Reconstruction.empty), bars)
  }

  test("writes reconstructed cells and marks their bars repaired") {
    val bars = Vector(bar(0), bar(1, close = 0.0), bar(2, open = 0.0), bar(3, high = 12.0, low = 9.0, volume = 0L))
    val tags = PriceRepair.detectZeroes(bars, daily)
    val recon = Reconstruction(
      priceCells = Map(((1, PriceColumn.Close): (Int, PriceColumn)) -> 10.5),
      volumeRows = Map(3 -> 500L)
    )
    val result = PriceRepair.applyReconstruction(bars, tags, recon)
    assertEquals(result(1).close, 10.5)
    assert(result(1).repaired, "a reconstructed price cell should mark its bar repaired")
    assertEquals(result(2), bars(2)) // uncovered tag restores the original, flag untouched
    assertEquals(result(3).volume, 500L)
    assert(result(3).repaired, "a reconstructed volume should mark its bar repaired")
  }

  test("restoring a cell does not clear a repair flag set by another pass") {
    val alreadyRepaired = bar(1, high = 12.0, low = 9.0, volume = 0L, repaired = true)
    val bars = Vector(bar(0), alreadyRepaired, bar(2, close = 0.0))
    val tags = PriceRepair.detectZeroes(bars, daily)
    assert(tags.volumeRows.contains(1), "fixture should tag the already-repaired bar's volume")
    val result = PriceRepair.applyReconstruction(bars, tags, Reconstruction.empty)
    assert(result(1).repaired, "the restore must not erase the earlier pass's repaired flag")
    assertEquals(result, bars)
  }

  test("exempts the first row from the close-move volume rule") {
    val bars = Vector(bar(0, volume = 0L), bar(1), bar(2))
    assert(PriceRepair.detectZeroes(bars, daily).isEmpty, "the first row has no predecessor to move against")
  }

  test("returns no tags for an empty table") {
    assertEquals(PriceRepair.detectZeroes(Vector.empty, daily), ZeroTags.empty)
  }
}
