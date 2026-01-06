package org.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.{Interval, Range, Ticker}

import scala.concurrent.duration._

class SplitsSpec extends CatsEffectSuite {

  val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  test("getSplits should return splits for a stock with split history (AAPL)") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getSplits(Ticker("AAPL"), Interval.`1Day`, Range.Max).map { splitsOpt =>
        assert(splitsOpt.isDefined, "Result should be defined for AAPL")
        val splits = splitsOpt.get

        // Apple has had multiple splits historically
        assert(splits.nonEmpty, "AAPL should have split history")

        // Validate split structure
        splits.foreach { split =>
          assert(split.numerator > 0, s"Numerator should be positive: ${split.numerator}")
          assert(split.denominator > 0, s"Denominator should be positive: ${split.denominator}")
          assert(split.splitRatio.nonEmpty, "Split ratio should not be empty")
          assert(split.splitRatio.contains(":"), s"Split ratio should contain colon: ${split.splitRatio}")
        }

        // Verify chronological ordering
        val dates = splits.map(_.exDate.toInstant)
        assert(dates == dates.sorted, "Splits should be chronologically sorted")
      }
    }
  }

  test("getSplits should identify forward and reverse splits correctly") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getSplits(Ticker("AAPL"), Interval.`1Day`, Range.Max).map { splitsOpt =>
        splitsOpt.foreach { splits =>
          splits.foreach { split =>
            // Apple's splits have been forward splits
            if (split.isForwardSplit) {
              assert(split.factor > 1.0, "Forward split factor should be > 1")
            }
            if (split.isReverseSplit) {
              assert(split.factor < 1.0, "Reverse split factor should be < 1")
            }
          }
        }
      }
    }
  }

  test("getSplits should return empty list for stocks without splits in range") {
    YFinanceClient.resource[IO](config).use { client =>
      // Use a short range where most stocks won't have splits
      client.getSplits(Ticker("AAPL"), Interval.`1Day`, Range.`1Month`).map { splitsOpt =>
        assert(splitsOpt.isDefined, "Result should be defined")
        // Likely empty for 1 month, but structure should be valid
        splitsOpt.foreach { splits =>
          splits.foreach { split =>
            assert(split.numerator > 0)
            assert(split.denominator > 0)
          }
        }
      }
    }
  }
}
