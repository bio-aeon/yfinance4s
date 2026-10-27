package io.github.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import io.github.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import io.github.coinductive.yfinance4s.models.{Interval, Range, Ticker}

import java.time.{ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.*

class ChartsSpec extends CatsEffectSuite {

  override val munitTimeout: Duration = 60.seconds

  private val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 1
  )

  private val since = ZonedDateTime.of(2026, 7, 27, 0, 0, 0, 0, ZoneOffset.UTC)
  private val until = ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC)

  test("getChart for VOD.L reads a window in which Yahoo reports a day without data") {
    // Yahoo reports no data for VOD.L on 2026-07-30.
    YFinanceClient.resource[IO](config).use { client =>
      client.charts.getChart(Ticker("VOD.L"), Interval.`1Day`, since, until).map { chart =>
        val quotes = chart.getOrElse(fail("expected chart data")).quotes
        assert(quotes.nonEmpty, "the window has trading days")
        quotes.foreach { q =>
          val prices = List(q.open, q.high, q.low, q.close, q.adjclose)
          assert(prices.forall(!_.isNaN), s"expected every price of the ${q.datetime} bar, got $prices")
        }
      }
    }
  }

  test("getChart for SPY reads hourly bars, which Yahoo sends without adjusted closes") {
    YFinanceClient.resource[IO](config).use { client =>
      client.charts.getChart(Ticker("SPY"), Interval.`1Hour`, Range.`5Days`).map { chart =>
        val quotes = chart.getOrElse(fail("expected chart data")).quotes
        assert(quotes.nonEmpty, "five days span trading hours")
        quotes.foreach(q => assertEquals(q.adjclose, q.close, s"adjusted close of the ${q.datetime} bar"))
      }
    }
  }

  test("getChart for BTC-USD marks today's daily bar in progress, as the market never closes") {
    YFinanceClient.resource[IO](config).use { client =>
      client.charts.getChart(Ticker("BTC-USD"), Interval.`1Day`, Range.`5Days`).map { chart =>
        val flags = chart.getOrElse(fail("expected chart data")).quotes.map(_.inProgress)
        assertEquals(flags.lastOption, Some(true), "today's bar")
        assert(!flags.init.contains(true), s"expected only today's bar in progress, got $flags")
      }
    }
  }

  test("getChart for SPY returns a chart without quotes for a weekend") {
    val weekend = ZonedDateTime.of(2026, 7, 25, 0, 0, 0, 0, ZoneOffset.UTC)
    YFinanceClient.resource[IO](config).use { client =>
      client.charts.getChart(Ticker("SPY"), Interval.`1Hour`, weekend, weekend.plusDays(2)).map { chart =>
        assertEquals(chart.map(_.quotes), Some(Nil))
      }
    }
  }

  test("getChart for SPY keeps the hourly bars of a past window inside it") {
    YFinanceClient.resource[IO](config).use { client =>
      client.charts.getChart(Ticker("SPY"), Interval.`1Hour`, since, until).map { chart =>
        val quotes = chart.getOrElse(fail("expected chart data")).quotes
        assert(quotes.nonEmpty, "the window has trading hours")
        assert(
          quotes.forall(_.datetime.isBefore(until)),
          s"expected no bar from $until on, got ${quotes.last.datetime}"
        )
      }
    }
  }
}
