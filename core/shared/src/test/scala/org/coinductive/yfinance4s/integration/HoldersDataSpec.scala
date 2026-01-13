package org.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.Ticker

import scala.concurrent.duration.*

class HoldersDataSpec extends CatsEffectSuite {

  val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  test("getMajorHolders should return ownership breakdown for AAPL") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getMajorHolders(Ticker("AAPL")).map { holdersOpt =>
        assert(holdersOpt.isDefined, "Result should be defined for AAPL")
        val holders = holdersOpt.get

        // Validate percentages are within valid range
        assert(
          holders.insidersPercentHeld >= 0.0 && holders.insidersPercentHeld <= 1.0,
          s"Insider percentage should be 0-100%: ${holders.insidersPercentHeld}"
        )
        assert(
          holders.institutionsPercentHeld >= 0.0 && holders.institutionsPercentHeld <= 1.0,
          s"Institution percentage should be 0-100%: ${holders.institutionsPercentHeld}"
        )

        // AAPL should have many institutional holders
        assert(
          holders.institutionsCount > 1000,
          s"AAPL should have many institutional holders: ${holders.institutionsCount}"
        )
      }
    }
  }

  test("getInstitutionalHolders should return top holders for MSFT") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getInstitutionalHolders(Ticker("MSFT")).map { holders =>
        // Should have institutional holders
        assert(holders.nonEmpty, "MSFT should have institutional holders")

        // Validate structure
        holders.foreach { holder =>
          assert(holder.organization.nonEmpty, "Organization name should not be empty")
          assert(holder.percentHeld >= 0.0, s"Percent held should be non-negative: ${holder.percentHeld}")
          assert(holder.sharesHeld >= 0L, s"Shares held should be non-negative: ${holder.sharesHeld}")
        }

        // Results should be sorted by percentage descending
        val percentages = holders.map(_.percentHeld)
        assert(percentages == percentages.sorted.reverse, "Holders should be sorted by percentage descending")
      }
    }
  }

  test("getMutualFundHolders should return fund holders for GOOGL") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getMutualFundHolders(Ticker("GOOGL")).map { holders =>
        // Should have mutual fund holders
        assert(holders.nonEmpty, "GOOGL should have mutual fund holders")

        // Validate structure
        holders.foreach { holder =>
          assert(holder.organization.nonEmpty, "Fund name should not be empty")
        }
      }
    }
  }

  test("getInsiderTransactions should return recent transactions for AAPL") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getInsiderTransactions(Ticker("AAPL")).map { transactions =>
        // AAPL typically has insider activity
        assert(transactions.nonEmpty, "AAPL should have insider transactions")

        // Validate structure
        transactions.foreach { tx =>
          assert(tx.filerName.nonEmpty, "Filer name should not be empty")
          assert(tx.shares != 0, "Shares should not be zero")
        }

        // Results should be sorted by date descending (most recent first)
        val dates = transactions.map(_.transactionDate)
        assert(dates == dates.sorted.reverse, "Transactions should be sorted by date descending")
      }
    }
  }

  test("getInsiderRoster should return insider positions for TSLA") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getInsiderRoster(Ticker("TSLA")).map { roster =>
        // Should have insider roster
        assert(roster.nonEmpty, "TSLA should have insider roster")

        // Validate structure
        roster.foreach { entry =>
          assert(entry.name.nonEmpty, "Insider name should not be empty")
          assert(entry.relation.nonEmpty, "Relation should not be empty")
        }
      }
    }
  }

  test("getHoldersData should return comprehensive data for NVDA") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getHoldersData(Ticker("NVDA")).map { dataOpt =>
        assert(dataOpt.isDefined, "Result should be defined for NVDA")
        val data = dataOpt.get

        // Should have at least some data
        assert(data.nonEmpty, "NVDA should have holders data")

        // Major holders should be available
        assert(data.majorHolders.isDefined, "NVDA should have major holders breakdown")
      }
    }
  }

  test("getHoldersData should work for stocks with limited holder data") {
    YFinanceClient.resource[IO](config).use { client =>
      // Use a smaller company that should still have some holder data
      client.getHoldersData(Ticker("IBM")).map { dataOpt =>
        assert(dataOpt.isDefined, "Result should be defined for IBM")
        // IBM should have at least some holders data
        assert(dataOpt.get.nonEmpty, "IBM should have holders data")
      }
    }
  }
}
