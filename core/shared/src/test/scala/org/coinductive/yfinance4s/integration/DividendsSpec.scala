package org.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.{Interval, Range, Ticker}

import java.time.ZonedDateTime
import scala.concurrent.duration._

class DividendsSpec extends CatsEffectSuite {

  val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  test("getDividends should return dividends for a dividend-paying stock (AAPL)") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getDividends(Ticker("AAPL"), Interval.`1Day`, Range.`1Year`).map { dividendsOpt =>
        assert(dividendsOpt.isDefined, "Result should be defined for AAPL")
        val dividends = dividendsOpt.get

        // AAPL pays quarterly dividends
        assert(dividends.nonEmpty, "AAPL should have at least one dividend in the past year")

        // Validate dividend structure
        dividends.foreach { div =>
          assert(div.amount > 0.0, s"Dividend amount should be positive: ${div.amount}")
          assert(
            div.exDate.isBefore(ZonedDateTime.now()) || div.exDate.isEqual(ZonedDateTime.now()),
            s"Ex-date should not be in the future: ${div.exDate}"
          )
        }

        // Verify chronological ordering
        val dates = dividends.map(_.exDate.toInstant)
        assert(dates == dates.sorted, "Dividends should be chronologically sorted")
      }
    }
  }

  test("getDividends with date range should filter appropriately") {
    YFinanceClient.resource[IO](config).use { client =>
      val now = ZonedDateTime.now()
      val sixMonthsAgo = now.minusMonths(6)

      client.getDividends(Ticker("AAPL"), Interval.`1Day`, sixMonthsAgo, now).map { dividendsOpt =>
        assert(dividendsOpt.isDefined, "Result should be defined")
        val dividends = dividendsOpt.get

        dividends.foreach { div =>
          assert(
            !div.exDate.isBefore(sixMonthsAgo.minusDays(1)),
            s"Dividend date ${div.exDate} should be after ${sixMonthsAgo}"
          )
          assert(
            !div.exDate.isAfter(now.plusDays(1)),
            s"Dividend date ${div.exDate} should be before ${now}"
          )
        }
      }
    }
  }

  test("getDividends should handle Max range for full dividend history") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getDividends(Ticker("AAPL"), Interval.`1Day`, Range.Max).map { dividendsOpt =>
        assert(dividendsOpt.isDefined, "Result should be defined")
        val dividends = dividendsOpt.get

        // Apple has been paying dividends since 2012, should have many records
        assert(dividends.size >= 20, s"AAPL should have extensive dividend history: ${dividends.size}")
      }
    }
  }
}
