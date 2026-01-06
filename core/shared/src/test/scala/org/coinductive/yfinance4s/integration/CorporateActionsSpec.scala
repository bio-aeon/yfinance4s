package org.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.{Interval, Range, Ticker}

import scala.concurrent.duration._

class CorporateActionsSpec extends CatsEffectSuite {

  val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  test("getCorporateActions should return combined dividends and splits") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getCorporateActions(Ticker("AAPL"), Interval.`1Day`, Range.`5Years`).map { actionsOpt =>
        assert(actionsOpt.isDefined, "Result should be defined for AAPL")
        val actions = actionsOpt.get

        // AAPL should have both dividends and possibly splits in 5 years
        assert(actions.nonEmpty, "Should have at least some corporate actions")
        assert(actions.dividends.nonEmpty, "AAPL should have dividends in 5 years")

        // Validate helper methods
        assert(actions.dividendCount == actions.dividends.size)
        assert(actions.splitCount == actions.splits.size)
        assert(actions.totalDividendAmount > 0, "Total dividend amount should be positive")
      }
    }
  }

  test("getCorporateActions cumulative split factor should calculate correctly") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getCorporateActions(Ticker("AAPL"), Interval.`1Day`, Range.Max).map { actionsOpt =>
        actionsOpt.foreach { actions =>
          val cumulativeFactor = actions.cumulativeSplitFactor

          if (actions.splits.nonEmpty) {
            // Should be product of individual factors
            val expectedFactor = actions.splits.map(_.factor).product
            assert(
              Math.abs(cumulativeFactor - expectedFactor) < 0.001,
              s"Cumulative factor $cumulativeFactor should equal product $expectedFactor"
            )
          } else {
            // No splits = factor of 1
            assert(cumulativeFactor == 1.0, "No splits should mean factor of 1.0")
          }
        }
      }
    }
  }

  test("getChart should include corporate actions in ChartResult") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getChart(Ticker("AAPL"), Interval.`1Day`, Range.`1Year`).map { chartOpt =>
        assert(chartOpt.isDefined, "Chart should be defined for AAPL")
        val chart = chartOpt.get

        // Verify quotes exist
        assert(chart.quotes.nonEmpty, "Should have price quotes")

        // Verify dividends are populated
        assert(chart.dividends.nonEmpty, "AAPL should have dividends embedded in chart")

        // Verify hasCorporateActions helper
        assert(chart.hasCorporateActions, "hasCorporateActions should be true")

        // Verify corporateActions helper method
        val actions = chart.corporateActions
        assertEquals(actions.dividends, chart.dividends)
        assertEquals(actions.splits, chart.splits)
      }
    }
  }
}
