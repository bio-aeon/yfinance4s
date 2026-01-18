package org.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.{Frequency, Ticker}

import scala.concurrent.duration.*

class FinancialStatementsSpec extends CatsEffectSuite {

  val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  test("getFinancialStatements should return yearly financial data for AAPL") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getFinancialStatements(Ticker("AAPL"), Frequency.Yearly).map { result =>
        assert(result.isDefined, "Result should be defined for AAPL")
        val financials = result.get

        assert(financials.nonEmpty, "AAPL should have financial statements")

        // Should have income statements
        assert(financials.incomeStatements.nonEmpty, "AAPL should have income statements")
        val latestIncome = financials.incomeStatements.head
        assert(latestIncome.totalRevenue.isDefined, "AAPL should have total revenue")
        assert(latestIncome.totalRevenue.get > 0, "Revenue should be positive")

        // Should have balance sheets
        assert(financials.balanceSheets.nonEmpty, "AAPL should have balance sheets")
        val latestBalance = financials.balanceSheets.head
        assert(latestBalance.totalAssets.isDefined, "AAPL should have total assets")
        assert(latestBalance.totalAssets.get > 0, "Total assets should be positive")

        // Should have cash flow statements
        assert(financials.cashFlowStatements.nonEmpty, "AAPL should have cash flow statements")
        val latestCashFlow = financials.cashFlowStatements.head
        assert(latestCashFlow.operatingCashFlow.isDefined, "AAPL should have operating cash flow")
      }
    }
  }

  test("getFinancialStatements should return quarterly data for MSFT") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getFinancialStatements(Ticker("MSFT"), Frequency.Quarterly).map { result =>
        assert(result.isDefined, "Result should be defined for MSFT")
        val financials = result.get

        assert(financials.nonEmpty, "MSFT should have financial statements")

        // Quarterly data should have more periods than yearly
        assert(financials.incomeStatements.size >= 4, "Should have at least 4 quarterly statements")

        // Verify statements are sorted by date descending
        val dates = financials.incomeStatements.map(_.reportDate)
        assert(dates == dates.sorted.reverse, "Statements should be sorted by date descending")
      }
    }
  }

  test("getIncomeStatements should return income data for GOOGL") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getIncomeStatements(Ticker("GOOGL")).map { statements =>
        assert(statements.nonEmpty, "GOOGL should have income statements")

        statements.foreach { stmt =>
          // Validate basic structure
          assert(stmt.currencyCode.nonEmpty, "Currency code should not be empty")
          assert(stmt.periodType.nonEmpty, "Period type should not be empty")

          // Revenue should be present for GOOGL
          stmt.totalRevenue.foreach { revenue =>
            assert(revenue > 0, s"Revenue should be positive: $revenue")
          }

          // Net income should be present
          stmt.netIncome.foreach { netIncome =>
            assert(netIncome != 0, "Net income should not be zero")
          }
        }

        // Test derived metrics on latest statement
        val latest = statements.head
        latest.grossMargin.foreach { margin =>
          assert(margin > 0 && margin < 1, s"Gross margin should be between 0 and 1: $margin")
        }
      }
    }
  }

  test("getBalanceSheets should return balance sheet data for NVDA") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getBalanceSheets(Ticker("NVDA")).map { sheets =>
        assert(sheets.nonEmpty, "NVDA should have balance sheets")

        sheets.foreach { sheet =>
          // Total assets should be present
          sheet.totalAssets.foreach { assets =>
            assert(assets > 0, s"Total assets should be positive: $assets")
          }

          // Stockholders equity should be present
          sheet.stockholdersEquity.foreach { equity =>
            assert(equity > 0, s"Stockholders equity should be positive for NVDA: $equity")
          }
        }

        // Test derived metrics on latest sheet
        val latest = sheets.head
        latest.currentRatio.foreach { ratio =>
          assert(ratio > 0, s"Current ratio should be positive: $ratio")
        }
        latest.debtToEquity.foreach { ratio =>
          assert(ratio >= 0, s"Debt to equity should be non-negative: $ratio")
        }
      }
    }
  }

  test("getCashFlowStatements should return cash flow data for AMZN") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getCashFlowStatements(Ticker("AMZN")).map { statements =>
        assert(statements.nonEmpty, "AMZN should have cash flow statements")

        statements.foreach { stmt =>
          // Operating cash flow should be present
          stmt.operatingCashFlow.foreach { ocf =>
            assert(ocf != 0, "Operating cash flow should not be zero")
          }
        }

        // Test derived metrics on latest statement
        val latest = statements.head
        latest.calculatedFreeCashFlow.foreach { fcf =>
          // FCF can be negative for growth companies, just verify it's calculated
          assert(!fcf.isNaN, "FCF should not be NaN")
        }
      }
    }
  }

  test("getFinancialStatements should support trailing frequency") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getFinancialStatements(Ticker("AAPL"), Frequency.Trailing).map { result =>
        assert(result.isDefined, "Trailing result should be defined for AAPL")
        val financials = result.get

        // Trailing should have data (TTM - trailing twelve months)
        assert(financials.nonEmpty, "AAPL should have trailing financial data")
      }
    }
  }

  test("cross-statement metrics should calculate correctly for AAPL") {
    YFinanceClient.resource[IO](config).use { client =>
      client.getFinancialStatements(Ticker("AAPL")).map { result =>
        assert(result.isDefined, "Result should be defined for AAPL")
        val financials = result.get

        // Test ROA (requires income and balance sheet)
        financials.returnOnAssets.foreach { roa =>
          assert(roa > 0 && roa < 1, s"ROA should be reasonable for AAPL: $roa")
        }

        // Test ROE
        financials.returnOnEquity.foreach { roe =>
          assert(roe > 0, s"ROE should be positive for profitable AAPL: $roe")
        }

        // Test asset turnover
        financials.assetTurnover.foreach { turnover =>
          assert(turnover > 0, s"Asset turnover should be positive: $turnover")
        }

        // Test interest coverage
        financials.interestCoverage.foreach { coverage =>
          assert(coverage > 0, s"Interest coverage should be positive for AAPL: $coverage")
        }
      }
    }
  }

  test("getFinancialStatements should work for stocks without all data") {
    YFinanceClient.resource[IO](config).use { client =>
      // Use a company that should have financials
      client.getFinancialStatements(Ticker("IBM")).map { result =>
        assert(result.isDefined, "Result should be defined for IBM")
        // IBM should have at least some financial data
        val financials = result.get
        assert(financials.nonEmpty, "IBM should have some financial data")
      }
    }
  }
}
