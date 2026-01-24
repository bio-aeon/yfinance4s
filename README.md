# YFinance4s

Effectful Yahoo Finance client in the Scala programming language.

## Features

- **Historical Data**: Fetch OHLCV (Open, High, Low, Close, Volume) price data with configurable intervals and date ranges
- **Corporate Actions**: Retrieve dividend and stock split history
- **Options Data**: Access option chains, expirations, and contract details (calls, puts, strikes, Greeks)
- **Stock Fundamentals**: Retrieve comprehensive company data including financials, valuation ratios, and key statistics
- **Holders Data**: Get institutional ownership, mutual fund holdings, and insider transactions
- **Financial Statements**: Fetch income statements, balance sheets, and cash flow statements (annual, quarterly, or trailing)
- **Purely Functional**: Built on Cats Effect 3 with `Resource`-based lifecycle management
- **Cross-Platform**: Supports both JVM and Scala.js
- **Scala 2.13 & Scala 3**: Compatible with Scala 2.13 and Scala 3

## Scala.js Setup

When using this library with Scala.js, you need to install the `node-html-parser` npm package:

```bash
npm install node-html-parser
```

## Usage

### Creating a Client

```scala
import cats.effect._
import org.coinductive.yfinance4s._
import org.coinductive.yfinance4s.models._
import scala.concurrent.duration._

val config = YFinanceClientConfig(
  connectTimeout = 10.seconds,
  readTimeout = 30.seconds,
  retries = 3
)

// The client is managed as a Resource for safe acquisition/release
val clientResource: Resource[IO, YFinanceClient[IO]] =
  YFinanceClient.resource[IO](config)
```

### Fetching Historical Chart Data

```scala
import org.coinductive.yfinance4s.models.{Interval, Range, Ticker}

clientResource.use { client =>
  // Fetch last year of daily data for Apple
  client.getChart(Ticker("AAPL"), Interval.`1Day`, Range.`1Year`).flatMap {
    case Some(chart) =>
      IO.println(s"Got ${chart.quotes.size} data points") *>
      chart.quotes.take(3).traverse_ { quote =>
        IO.println(s"${quote.datetime}: Open=${quote.open}, Close=${quote.close}, Volume=${quote.volume}")
      }
    case None =>
      IO.println("No data found")
  }
}
```

### Fetching Data by Date Range

```scala
import java.time.ZonedDateTime

clientResource.use { client =>
  val since = ZonedDateTime.parse("2024-01-01T00:00:00Z")
  val until = ZonedDateTime.parse("2024-12-01T00:00:00Z")

  client.getChart(Ticker("MSFT"), Interval.`1Day`, since, until)
}
```

### Fetching Stock Fundamentals

```scala
clientResource.use { client =>
  client.getStock(Ticker("GOOGL")).map {
    case Some(stock) =>
      println(s"${stock.longName} (${stock.symbol})")
      println(s"Price: ${stock.currency} ${stock.regularMarketPrice}")
      println(s"Market Cap: ${stock.marketCap}")
      println(s"Sector: ${stock.sector.getOrElse("N/A")}")
      println(s"P/E Ratio: ${stock.trailingPE.getOrElse("N/A")}")
      println(s"Dividend Yield: ${stock.dividendYield.map(y => f"${y * 100}%.2f%%").getOrElse("N/A")}")
    case None =>
      println("Stock not found")
  }
}
```

### Fetching Dividends and Splits

```scala
clientResource.use { client =>
  // Fetch dividend history
  client.getDividends(Ticker("AAPL"), Interval.`1Day`, Range.`5Years`).map {
    case Some(dividends) =>
      dividends.foreach { div =>
        println(s"${div.exDate}: $${div.amount}")
      }
    case None => println("No dividend data")
  }
}

clientResource.use { client =>
  // Fetch stock split history
  client.getSplits(Ticker("TSLA"), Interval.`1Day`, Range.Max).map {
    case Some(splits) =>
      splits.foreach { split =>
        println(s"${split.exDate}: ${split.splitRatio} (${if (split.isForwardSplit) "forward" else "reverse"})")
      }
    case None => println("No split data")
  }
}

clientResource.use { client =>
  // Fetch both dividends and splits together
  client.getCorporateActions(Ticker("MSFT"), Interval.`1Day`, Range.`10Years`).map {
    case Some(actions) =>
      println(s"Dividends: ${actions.dividends.size}, Splits: ${actions.splits.size}")
    case None => println("No corporate actions")
  }
}
```

### Fetching Options Data

```scala
clientResource.use { client =>
  // Get all available expiration dates
  client.getOptionExpirations(Ticker("SPY")).flatMap {
    case Some(expirations) =>
      IO.println(s"Available expirations: ${expirations.take(5).mkString(", ")}...")
    case None =>
      IO.println("No options available")
  }
}

clientResource.use { client =>
  // Get option chain for a specific expiration
  client.getOptionChain(Ticker("AAPL"), LocalDate.of(2025, 1, 17)).map {
    case Some(chain) =>
      println(s"Expiration: ${chain.expirationDate}")
      println(s"Calls: ${chain.calls.size}, Puts: ${chain.puts.size}")
      println(s"Put/Call Ratio: ${chain.putCallRatio.getOrElse("N/A")}")
      chain.calls.take(3).foreach { c =>
        println(s"  Call $${c.strike}: bid=${c.bid}, ask=${c.ask}, IV=${c.impliedVolatility.map(v => f"$v%.1f%%")}")
      }
    case None =>
      println("Option chain not found")
  }
}

clientResource.use { client =>
  // Get full option chain with all expirations
  client.getFullOptionChain(Ticker("NVDA")).map {
    case Some(full) =>
      println(s"Underlying: ${full.underlyingSymbol} @ ${full.underlyingPrice}")
      println(s"Expirations: ${full.expirationCount}")
      full.nearestChain.foreach { chain =>
        println(s"Nearest expiration: ${chain.expirationDate}")
      }
    case None =>
      println("No options data")
  }
}
```

### Fetching Holders Data

```scala
clientResource.use { client =>
  // Get major holders breakdown
  client.getMajorHolders(Ticker("AAPL")).map {
    case Some(holders) =>
      println(f"Insiders: ${holders.insidersPercentHeld * 100}%.2f%%")
      println(f"Institutions: ${holders.institutionsPercentHeld * 100}%.2f%%")
      println(s"Institution count: ${holders.institutionsCount}")
    case None =>
      println("No holders data")
  }
}

clientResource.use { client =>
  // Get top institutional holders
  client.getInstitutionalHolders(Ticker("MSFT")).flatMap { holders =>
    IO.println(s"Top ${holders.size} institutional holders:") *>
    holders.take(5).traverse_ { h =>
      IO.println(f"  ${h.organization}: ${h.percentHeld * 100}%.2f%% (${h.shares} shares)")
    }
  }
}

clientResource.use { client =>
  // Get recent insider transactions
  client.getInsiderTransactions(Ticker("GOOGL")).flatMap { transactions =>
    transactions.take(5).traverse_ { t =>
      val action = if (t.isPurchase) "bought" else "sold"
      IO.println(s"${t.filerName} $action ${t.shares} shares on ${t.transactionDate}")
    }
  }
}

clientResource.use { client =>
  // Get comprehensive holders data
  client.getHoldersData(Ticker("AMZN")).map {
    case Some(data) =>
      println(s"Institutional holders: ${data.institutionalHolders.size}")
      println(s"Mutual fund holders: ${data.mutualFundHolders.size}")
      println(s"Insider transactions: ${data.insiderTransactions.size}")
      println(f"Net insider shares: ${data.netInsiderShares}%+d")
    case None =>
      println("No holders data")
  }
}
```

### Fetching Financial Statements

```scala
import org.coinductive.yfinance4s.models.Frequency

clientResource.use { client =>
  // Get all financial statements (annual by default)
  client.getFinancialStatements(Ticker("AAPL")).map {
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

clientResource.use { client =>
  // Get quarterly income statements
  client.getIncomeStatements(Ticker("MSFT"), Frequency.Quarterly).flatMap { statements =>
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

clientResource.use { client =>
  // Get balance sheets
  client.getBalanceSheets(Ticker("GOOGL"), Frequency.Yearly).flatMap { sheets =>
    sheets.headOption.traverse_ { bs =>
      IO.println(s"As of: ${bs.reportDate}") *>
      IO.println(s"Cash: ${bs.cashAndCashEquivalents}") *>
      IO.println(s"Total Assets: ${bs.totalAssets}") *>
      IO.println(s"Total Debt: ${bs.totalDebt}") *>
      IO.println(s"Stockholders Equity: ${bs.stockholdersEquity}")
    }
  }
}

clientResource.use { client =>
  // Get cash flow statements
  client.getCashFlowStatements(Ticker("AMZN"), Frequency.Yearly).flatMap { statements =>
    statements.headOption.traverse_ { cf =>
      IO.println(s"Operating Cash Flow: ${cf.operatingCashFlow}") *>
      IO.println(s"Capital Expenditure: ${cf.capitalExpenditure}") *>
      IO.println(s"Free Cash Flow: ${cf.freeCashFlow}")
    }
  }
}
```

## Available Intervals

| Interval | Value |
|----------|-------|
| `1Minute` | 1m |
| `2Minutes` | 2m |
| `5Minutes` | 5m |
| `15Minutes` | 15m |
| `30Minutes` | 30m |
| `60Minutes` | 60m |
| `90Minutes` | 90m |
| `1Hour` | 1h |
| `1Day` | 1d |
| `5Days` | 5d |
| `1Week` | 1wk |
| `1Month` | 1mo |
| `3Months` | 3mo |

## Available Ranges

| Range | Value |
|-------|-------|
| `1Day` | 1d |
| `5Days` | 5d |
| `1Month` | 1mo |
| `3Months` | 3mo |
| `6Months` | 6mo |
| `1Year` | 1y |
| `2Years` | 2y |
| `5Years` | 5y |
| `10Years` | 10y |
| `YearToDate` | ytd |
| `Max` | max |

## Available Frequencies

| Frequency | Description |
|-----------|-------------|
| `Yearly` | Annual financial data (up to 4 years of history) |
| `Quarterly` | Quarterly financial data (up to 5 quarters of history) |
| `Trailing` | Trailing twelve months (TTM) data |

## Data Models

### ChartResult

Contains historical OHLCV data and corporate actions:
- `quotes`: List of OHLCV price quotes (datetime, open, high, low, close, volume, adjclose)
- `dividends`: List of dividend events within the chart period
- `splits`: List of stock split events within the chart period
- `corporateActions`: Combined dividends and splits as `CorporateActions`

### DividendEvent

Represents a dividend payment:
- `exDate`: The ex-dividend date
- `amount`: Dividend amount per share
- `yieldAt(sharePrice)`: Calculate dividend yield

### SplitEvent

Represents a stock split:
- `exDate`: Effective date of the split
- `numerator`, `denominator`: Split ratio components
- `splitRatio`: Human-readable ratio (e.g., "4:1")
- `factor`: Split factor as decimal
- `isForwardSplit`, `isReverseSplit`: Split direction

### StockResult

Comprehensive stock information including:
- Basic info: symbol, name, exchange, currency
- Price data: current price, change percent, market cap
- Company profile: sector, industry, business summary
- Valuation: P/E ratios, price-to-book, PEG ratio
- Financials: revenue, EBITDA, cash, debt
- Margins: gross, operating, profit, EBITDA margins
- Growth: earnings growth, revenue growth
- Shares: outstanding, float, short interest

### OptionChain

Option chain for a specific expiration date:
- `expirationDate`: The expiration date
- `calls`, `puts`: Lists of option contracts
- `strikes`: Available strike prices
- `putCallRatio`: Volume-based put/call ratio
- Helper methods: `itmCalls`, `otmCalls`, `itmPuts`, `otmPuts`, `straddleAtStrike(strike)`

### OptionContract

Individual option contract details:
- `contractSymbol`, `optionType` (Call/Put), `strike`, `expiration`
- Pricing: `lastPrice`, `bid`, `ask`, `spread`, `midPrice`
- Volume: `volume`, `openInterest`
- `impliedVolatility`: IV as percentage
- `inTheMoney`: Whether option is ITM
- Calculations: `intrinsicValue(price)`, `extrinsicValue(price)`, `notionalValue`

### FullOptionChain

Complete option data with all expirations:
- `underlyingSymbol`, `underlyingPrice`
- `expirationDates`: All available expiration dates
- `chains`: Map of expiration date to `OptionChain`
- Helper methods: `nearestExpiration`, `nearestChain`, `chainsWithinDays(days)`

### HoldersData

Comprehensive ownership data:
- `majorHolders`: Ownership breakdown by category
- `institutionalHolders`: Top institutional investors
- `mutualFundHolders`: Top mutual fund investors
- `insiderTransactions`: Recent insider buy/sell activity
- `insiderRoster`: Current insider positions
- Analytics: `netInsiderShares`, `insiderPurchases`, `insiderSales`

### MajorHolders

Ownership breakdown:
- `insidersPercentHeld`: Insider ownership percentage
- `institutionsPercentHeld`: Institutional ownership percentage
- `institutionsFloatPercentHeld`: Institutional ownership of float
- `institutionsCount`: Number of institutional holders

### FinancialStatements

All financial statements for a company:
- `ticker`, `currency`
- `incomeStatements`: List of income statements
- `balanceSheets`: List of balance sheets
- `cashFlowStatements`: List of cash flow statements
- Computed ratios: `returnOnAssets`, `returnOnEquity`, `assetTurnover`, `interestCoverage`

### IncomeStatement

Income statement data including:
- Revenue: `totalRevenue`, `costOfRevenue`, `grossProfit`
- Operating: `operatingExpense`, `operatingIncome`
- Earnings: `netIncome`, `ebit`, `ebitda`, `basicEps`, `dilutedEps`
- Margins and interest expense

### BalanceSheet

Balance sheet data including:
- Assets: `totalAssets`, `currentAssets`, `cashAndCashEquivalents`, `inventory`
- Liabilities: `totalLiabilities`, `currentLiabilities`, `totalDebt`
- Equity: `stockholdersEquity`, `retainedEarnings`

### CashFlowStatement

Cash flow data including:
- Operating: `operatingCashFlow`, `changeInWorkingCapital`
- Investing: `investingCashFlow`, `capitalExpenditure`
- Financing: `financingCashFlow`, `dividendsPaid`
- `freeCashFlow`: Operating cash flow minus capex

## License

See LICENSE file for details.
