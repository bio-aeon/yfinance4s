# Getting Started

YFinance4s is an effectful Yahoo Finance client for Scala, built on Cats Effect 3.

## Features

- **Historical Data**: OHLCV price data with configurable intervals and date ranges
- **Corporate Actions**: Dividend and stock split history
- **Options Data**: Option chains, expirations, and contract details (calls, puts, strikes, Greeks)
- **Stock Fundamentals**: Company data including financials, valuation ratios, and key statistics
- **Holders Data**: Institutional ownership, mutual fund holdings, and insider transactions
- **Financial Statements**: Income statements, balance sheets, and cash flow statements (annual, quarterly, trailing)
- **Analyst Data**: Price targets, recommendations, earnings estimates, upgrade/downgrade history, growth comparisons
- **Sector Data**: Sector overview, top ETFs, mutual funds, industries, and top companies for all 11 GICS sectors
- **Industry Data**: Per-industry overview, top companies, top performers (YTD return, implied upside), and top growth companies
- **Market Data**: Region-level market summary (headline indices), trading status with open/close times, and trending tickers
- **Earnings Calendar**: Market-wide upcoming earnings ranked by market cap, and per-ticker historical/upcoming earnings timelines
- **Search**: Ticker/company/news search with fuzzy matching
- **Stock Screener**: Custom and predefined equity/fund screens
- **ISIN Lookup**: Resolve ISINs to Yahoo Finance tickers with ISO 6166 validation
- **Batch Operations**: Parallel multi-ticker fetches with configurable concurrency and error-tolerant modes

## Platform Support

- **Scala 2.13 & Scala 3**
- **JVM and Scala.js**

When using Scala.js, install the `node-html-parser` npm package:

```bash
npm install node-html-parser
```

## Creating a Client

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

## Quick Example

```scala
clientResource.use { client =>
  client.charts.getChart(Ticker("AAPL"), Interval.`1Day`, Range.`1Year`).flatMap {
    case Some(chart) =>
      IO.println(s"Got ${chart.quotes.size} data points for AAPL")
    case None =>
      IO.println("No data found")
  }
}
```

## Client API Overview

The client exposes domain-specific modules:

```scala
client.charts      // historical data, quotes, dividends, splits
client.options     // option chains and expirations
client.holders     // institutional, mutual fund, and insider data
client.financials  // income statements, balance sheets, cash flows
client.analysts    // price targets, recommendations, estimates
client.sectors     // sector overview, industries, top ETFs/funds
client.industries  // industry overview, top performers, top growth companies
client.markets     // region summary, market status, trending tickers
client.calendars   // earnings calendar and per-ticker earnings timelines
client.screener    // custom and predefined stock/fund screens
client.search(q)   // ticker and news search
```
