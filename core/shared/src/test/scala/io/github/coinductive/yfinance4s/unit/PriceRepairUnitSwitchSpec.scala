package io.github.coinductive.yfinance4s.unit

import munit.FunSuite
import io.github.coinductive.yfinance4s.PriceRepair
import io.github.coinductive.yfinance4s.PriceRepair.{Bar, Context}
import io.github.coinductive.yfinance4s.models.Interval

import java.time.{ZoneOffset, ZonedDateTime}

class PriceRepairUnitSwitchSpec extends FunSuite {

  private val start = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
  private val daily = Context(currency = "USD", interval = Interval.`1Day`, isFx = false)

  /** A flat bar (all OHLC and adj close equal); `i` is the chronological index. */
  private def flatBar(
      i: Int,
      price: Double,
      volume: Long = 1000L,
      dividend: Double = 0.0,
      splitRatio: Double = 0.0,
      hourly: Boolean = false
  ): Bar =
    Bar(
      datetime = if (hourly) start.plusHours(i.toLong) else start.plusDays(i.toLong),
      open = price,
      high = price,
      low = price,
      close = price,
      adjClose = price,
      volume = volume,
      dividend = dividend,
      splitRatio = splitRatio,
      repaired = false
    )

  /** Chronological bars: the older block at `oldPrice`, the newer block at `newPrice`. */
  private def switchedBars(oldPrice: Double, newPrice: Double, blockSize: Int = 30): Vector[Bar] =
    (0 until 2 * blockSize).toVector.map(i => flatBar(i, if (i < blockSize) oldPrice else newPrice))

  private def assertClose(obtained: Double, expected: Double): Unit =
    assert(math.abs(obtained - expected) < 0.001, s"expected ~$expected, got $obtained")

  test("rescales the block after a systematic 100x switch") {
    val bars = switchedBars(oldPrice = 10000.0, newPrice = 100.0)
    val result = PriceRepair.fixUnitSwitch(bars, daily)
    (0 until 30).foreach { i =>
      assertClose(result(i).close, 100.0)
      assert(result(i).repaired, s"old-block bar $i should be repaired")
    }
    (30 until 60).foreach { i =>
      assertEquals(result(i), bars(i))
    }
  }

  test("rescales a switch in hourly bars") {
    val hourlyCtx = Context(currency = "USD", interval = Interval.`1Hour`, isFx = false)
    val bars = (0 until 60).toVector.map(i => flatBar(i, if (i < 30) 10000.0 else 100.0, hourly = true))
    val result = PriceRepair.fixUnitSwitch(bars, hourlyCtx)
    (0 until 30).foreach(i => assertClose(result(i).close, 100.0))
    (30 until 60).foreach(i => assertEquals(result(i), bars(i)))
  }

  test("rescales only the middle block of a switch and switch-back") {
    val bars = (0 until 90).toVector.map(i => flatBar(i, if (i >= 30 && i < 60) 10000.0 else 100.0))
    val result = PriceRepair.fixUnitSwitch(bars, daily)
    (30 until 60).foreach { i =>
      assertClose(result(i).close, 100.0)
      assert(result(i).repaired, s"middle-block bar $i should be repaired")
    }
    ((0 until 30) ++ (60 until 90)).foreach(i => assertEquals(result(i), bars(i)))
  }

  test("uses a 1000x factor for a Kuwaiti Dinar switch") {
    val kwf = Context(currency = "KWF", interval = Interval.`1Day`, isFx = false)
    val bars = switchedBars(oldPrice = 100000.0, newPrice = 100.0)
    val result = PriceRepair.fixUnitSwitch(bars, kwf)
    (0 until 30).foreach(i => assertClose(result(i).close, 100.0))
    (30 until 60).foreach(i => assertEquals(result(i), bars(i)))
  }

  test("leaves a volatile series without a switch-scale change untouched") {
    val bars = (0 until 20).toVector.map(i => flatBar(i, if (i % 2 == 0) 10.0 else 30.0))
    assertEquals(PriceRepair.fixUnitSwitch(bars, daily), bars)
  }

  test("unflags a jump that matches locally elevated volatility") {
    // Newest-first change ratios: tame (1.0) everywhere except a locally volatile stretch of 6x-9x
    // oscillations surrounding a single 52x jump. The local recheck's threshold rises above 52 there.
    val revChanges = Vector.tabulate(60) {
      case 40 => 8.0
      case 41 => 0.125
      case 42 => 9.0
      case 43 => 1.0 / 9.0
      case 44 => 7.0
      case 45 => 1.0 / 7.0
      case 46 => 52.0
      case 47 => 6.0
      case _  => 1.0
    }
    val revPrices = revChanges.tail.scanLeft(100.0)((price, change) => price * change)
    val bars = revPrices.reverse.zipWithIndex.map { case (price, i) => flatBar(i, price) }
    assertEquals(PriceRepair.fixUnitSwitch(bars, daily), bars)

    // The identical jump amid a tame neighbourhood stays flagged and is repaired.
    val tameRevPrices = Vector.tabulate(60)(i => if (i >= 46) 5200.0 else 100.0)
    val tameBars = tameRevPrices.reverse.zipWithIndex.map { case (price, i) => flatBar(i, price) }
    val repaired = PriceRepair.fixUnitSwitch(tameBars, daily)
    assert(repaired.exists(_.repaired), "the same jump amid tame volatility should be repaired")
  }

  test("scales coincident dividends with the block") {
    val bars = switchedBars(oldPrice = 10000.0, newPrice = 100.0)
      .updated(10, flatBar(10, 10000.0, dividend = 500.0))
    val result = PriceRepair.fixUnitSwitch(bars, daily)
    assertClose(result(10).dividend, 5.0)
    assert(result(10).repaired, "the dividend-carrying bar should be repaired with its block")
  }

  test("aborts when the flagged change follows a split within thirty days") {
    val bars = switchedBars(oldPrice = 10000.0, newPrice = 100.0)
      .updated(20, flatBar(20, 10000.0, splitRatio = 4.0))
    assertEquals(PriceRepair.fixUnitSwitch(bars, daily), bars)
  }

  test("repairs when the flagged change precedes the split") {
    val bars = switchedBars(oldPrice = 10000.0, newPrice = 100.0)
      .updated(40, flatBar(40, 100.0, splitRatio = 4.0))
    val result = PriceRepair.fixUnitSwitch(bars, daily)
    (0 until 30).foreach { i =>
      assertClose(result(i).close, 100.0)
      assert(result(i).repaired, s"old-block bar $i should be repaired despite the later split")
    }
  }

  test("skips the switch repair when the series appears suspended") {
    val bars = switchedBars(oldPrice = 10000.0, newPrice = 100.0)
      .updated(59, flatBar(59, 100.0, volume = 0L))
    assertEquals(PriceRepair.fixUnitSwitch(bars, daily), bars)
  }

  test("returns empty and single-row tables unchanged") {
    assertEquals(PriceRepair.fixUnitSwitch(Vector.empty, daily), Vector.empty[Bar])
    val single = Vector(flatBar(0, 10000.0))
    assertEquals(PriceRepair.fixUnitSwitch(single, daily), single)
  }
}
