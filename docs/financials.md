# Financial Statements

The `financials` module provides income statements, balance sheets, and cash flow statements with annual, quarterly, and trailing frequencies.

## All Statements

```scala
import org.coinductive.yfinance4s.models.Frequency

clientResource.use { client =>
  client.financials.getFinancialStatements(Ticker("AAPL")).map {
    case Some(statements) =>
      println(s"Currency: ${statements.currency}")
      println(s"Periods available: ${statements.periodCount}")

      statements.latestIncomeStatement.foreach { is =>
        println(s"Latest Revenue: ${is.totalRevenue}")
        println(s"Latest Net Income: ${is.netIncome}")
      }

      statements.latestBalanceSheet.foreach { bs =>
        println(s"Total Assets: ${bs.totalAssets}")
        println(s"Total Liabilities: ${bs.totalLiabilities}")
      }
    case None =>
      println("No financial data")
  }
}
```

## Income Statements

```scala
clientResource.use { client =>
  client.financials.getIncomeStatements(Ticker("MSFT"), Frequency.Quarterly).flatMap { statements =>
    IO.println(s"Found ${statements.size} quarterly income statements") *>
    statements.headOption.traverse_ { is =>
      IO.println(s"Period: ${is.reportDate} (${is.periodType})") *>
      IO.println(s"Revenue: ${is.totalRevenue}") *>
      IO.println(s"Gross Profit: ${is.grossProfit}") *>
      IO.println(s"Operating Income: ${is.operatingIncome}") *>
      IO.println(s"Net Income: ${is.netIncome}")
    }
  }
}
```

## Balance Sheets

```scala
clientResource.use { client =>
  client.financials.getBalanceSheets(Ticker("GOOGL"), Frequency.Yearly).flatMap { sheets =>
    sheets.headOption.traverse_ { bs =>
      IO.println(s"As of: ${bs.reportDate}") *>
      IO.println(s"Cash: ${bs.cashAndCashEquivalents}") *>
      IO.println(s"Total Assets: ${bs.totalAssets}") *>
      IO.println(s"Total Debt: ${bs.totalDebt}") *>
      IO.println(s"Stockholders Equity: ${bs.stockholdersEquity}")
    }
  }
}
```

## Cash Flow Statements

```scala
clientResource.use { client =>
  client.financials.getCashFlowStatements(Ticker("AMZN"), Frequency.Yearly).flatMap { statements =>
    statements.headOption.traverse_ { cf =>
      IO.println(s"Operating Cash Flow: ${cf.operatingCashFlow}") *>
      IO.println(s"Capital Expenditure: ${cf.capitalExpenditure}") *>
      IO.println(s"Free Cash Flow: ${cf.freeCashFlow}")
    }
  }
}
```
