package org.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.Ticker

import scala.concurrent.duration.*

class AnalystDataSpec extends CatsEffectSuite {

  val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  test("getAnalystPriceTargets should return targets for AAPL") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getAnalystPriceTargets(Ticker("AAPL")).map { result =>
        assert(result.isDefined, "Result should be defined for AAPL")
        val targets = result.get

        assert(targets.currentPrice > 0, s"Current price should be positive: ${targets.currentPrice}")
        assert(targets.targetHigh > targets.targetLow, "Target high should exceed target low")
        assert(
          targets.targetMean >= targets.targetLow && targets.targetMean <= targets.targetHigh,
          s"Target mean ${targets.targetMean} should be between low ${targets.targetLow} and high ${targets.targetHigh}"
        )
        assert(targets.numberOfAnalysts > 0, s"Should have analyst coverage: ${targets.numberOfAnalysts}")
        assert(targets.recommendationKey.nonEmpty, "Recommendation key should not be empty")
      }
    }
  }

  test("getRecommendations should return trends for MSFT") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getRecommendations(Ticker("MSFT")).map { recommendations =>
        assert(recommendations.nonEmpty, "MSFT should have recommendation trends")

        // Should have a current month entry
        val current = recommendations.find(_.period == "0m")
        assert(current.isDefined, "Should have a current month (0m) entry")
        assert(current.get.totalAnalysts > 0, s"Current month should have analysts: ${current.get.totalAnalysts}")

        // Should be sorted with current month first
        assertEquals(recommendations.head.period, "0m")
      }
    }
  }

  test("getUpgradeDowngradeHistory should return history for GOOGL") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getUpgradeDowngradeHistory(Ticker("GOOGL")).map { history =>
        assert(history.nonEmpty, "GOOGL should have upgrade/downgrade history")

        // Validate structure
        history.foreach { entry =>
          assert(entry.firm.nonEmpty, "Firm name should not be empty")
          assert(entry.toGrade.nonEmpty, "To grade should not be empty")
        }

        // Results should be sorted by date descending
        val dates = history.map(_.date)
        assert(dates == dates.sorted.reverse, "History should be sorted by date descending")
      }
    }
  }

  test("getEarningsEstimates should return estimates for NVDA") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getEarningsEstimates(Ticker("NVDA")).map { estimates =>
        assert(estimates.nonEmpty, "NVDA should have earnings estimates")

        // Should have both quarterly and yearly estimates
        assert(estimates.exists(_.isQuarterly), "Should have quarterly estimates")
        assert(estimates.exists(_.isYearly), "Should have yearly estimates")

        // Well-covered stock should have analyst count
        estimates.foreach { est =>
          est.numberOfAnalysts.foreach { n =>
            assert(n > 0, s"Number of analysts should be positive: $n")
          }
        }
      }
    }
  }

  test("getRevenueEstimates should return estimates for AMZN") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getRevenueEstimates(Ticker("AMZN")).map { estimates =>
        assert(estimates.nonEmpty, "AMZN should have revenue estimates")

        // Large company should have positive revenue estimates
        estimates.foreach { est =>
          est.avg.foreach { avg =>
            assert(avg > 0, s"Average revenue should be positive: $avg")
          }
        }
      }
    }
  }

  test("getEarningsHistory should return historical results for AAPL") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getEarningsHistory(Ticker("AAPL")).map { history =>
        assert(history.nonEmpty, "AAPL should have earnings history")

        // Validate structure
        history.foreach { entry =>
          assert(entry.epsActual.isDefined, "EPS actual should be defined for AAPL")
        }

        // Should be sorted by quarter descending
        val quarters = history.map(_.quarter)
        assert(quarters == quarters.sorted.reverse, "History should be sorted by quarter descending")
      }
    }
  }

  test("getGrowthEstimates should return growth data for MSFT") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getGrowthEstimates(Ticker("MSFT")).map { estimates =>
        assert(estimates.nonEmpty, "MSFT should have growth estimates")

        // Should have stock growth for at least one period
        assert(estimates.exists(_.stockGrowth.isDefined), "Should have stock growth data")
      }
    }
  }

  test("getAnalystData should return comprehensive data for NVDA") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getAnalystData(Ticker("NVDA")).map { dataOpt =>
        assert(dataOpt.isDefined, "Result should be defined for NVDA")
        val data = dataOpt.get

        assert(data.nonEmpty, "NVDA should have analyst data")
        assert(data.priceTargets.isDefined, "NVDA should have price targets")
        assert(data.recommendations.nonEmpty, "NVDA should have recommendations")
        assert(data.earningsEstimates.nonEmpty, "NVDA should have earnings estimates")
      }
    }
  }

  test("getAnalystData should work for stocks with limited analyst data") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getAnalystData(Ticker("IBM")).map { dataOpt =>
        assert(dataOpt.isDefined, "Result should be defined for IBM")
        assert(dataOpt.get.nonEmpty, "IBM should have analyst data")
      }
    }
  }
}
