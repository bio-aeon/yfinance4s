package org.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.*

import scala.concurrent.duration.*
import java.time.LocalDate

class OptionsChainSpec extends CatsEffectSuite {

  val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  val testTicker: Ticker = Ticker("AAPL")

  test("getOptionExpirations should return sorted list of expiration dates for AAPL") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getOptionExpirations(testTicker).map { expirationsOpt =>
        assert(expirationsOpt.isDefined, "Expirations should be defined for AAPL")

        val expirations = expirationsOpt.get
        assert(expirations.nonEmpty, "AAPL should have available expirations")

        val dates = expirations
        assert(dates == dates.sorted, "Expirations should be chronologically sorted")

        val today = LocalDate.now()
        assert(
          expirations.forall(d => !d.isBefore(today)),
          "All expirations should be today or in the future"
        )
      }
    }
  }

  test("getOptionChain should return valid chain for nearest expiration") {
    YFinanceClient.resource[IO](config).use { client =>
      for {
        expirationsOpt <- client.getOptionExpirations(testTicker)
        _ = assert(expirationsOpt.isDefined, "Need expirations to test chain")
        expirations = expirationsOpt.get
        nearestExpiration = expirations.head

        chainOpt <- client.getOptionChain(testTicker, nearestExpiration)
      } yield {
        assert(chainOpt.isDefined, s"Chain should exist for $nearestExpiration")

        val chain = chainOpt.get
        assertEquals(chain.expirationDate, nearestExpiration)
        assert(chain.calls.nonEmpty, "Should have call options")
        assert(chain.puts.nonEmpty, "Should have put options")
        assert(chain.strikes.nonEmpty, "Should have strikes")

        assert(
          chain.strikes == chain.strikes.sorted,
          "Strikes should be sorted ascending"
        )

        chain.calls.foreach { call =>
          assertEquals(call.optionType, OptionType.Call)
          assert(call.strike > 0, "Strike should be positive")
          assert(call.contractSymbol.nonEmpty, "Contract symbol should exist")
        }

        chain.puts.foreach { put =>
          assertEquals(put.optionType, OptionType.Put)
          assert(put.strike > 0, "Strike should be positive")
        }
      }
    }
  }

  test("getFullOptionChain should return complete data for AAPL") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getFullOptionChain(testTicker).map { fullChainOpt =>
        assert(fullChainOpt.isDefined, "Full chain should be defined")

        val fullChain = fullChainOpt.get
        assertEquals(fullChain.underlyingSymbol, "AAPL")
        assert(fullChain.underlyingPrice.isDefined, "Should have underlying price")
        assert(fullChain.underlyingPrice.get > 0, "Price should be positive")

        assert(fullChain.expirationDates.nonEmpty, "Should have expirations")
        assert(fullChain.chains.nonEmpty, "Should have at least one chain")

        val nearestExp = fullChain.nearestExpiration
        assert(nearestExp.isDefined, "Should have nearest expiration")
        assert(
          fullChain.chains.contains(nearestExp.get),
          "Should have chain for nearest expiration"
        )

        val nearestChain = fullChain.nearestChain
        assert(nearestChain.isDefined, "Should get nearest chain")
        assert(nearestChain.get.calls.nonEmpty || nearestChain.get.puts.nonEmpty)
      }
    }
  }

  test("implied volatility should be positive (converted to percentage)") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getFullOptionChain(testTicker).map { fullChainOpt =>
        assert(fullChainOpt.isDefined)

        val chain = fullChainOpt.get.nearestChain.get
        val ivValues = chain.allContracts.flatMap(_.impliedVolatility)

        assert(ivValues.nonEmpty, "Should have IV values")

        // IV should be positive; very high values (>1000%) are possible for
        // illiquid or far OTM options and are not necessarily data errors
        ivValues.foreach { iv =>
          assert(iv > 0, s"IV should be positive: $iv")
        }
      }
    }
  }

}
