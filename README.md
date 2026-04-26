# YFinance4s

Effectful Yahoo Finance client in the Scala programming language.

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
- **Purely Functional**: Built on Cats Effect 3 with `Resource`-based lifecycle management
- **Cross-Platform**: JVM and Scala.js
- **Scala 2.13 & Scala 3**

## Quick Start

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

YFinanceClient.resource[IO](config).use { client =>
  for {
    chart   <- client.charts.getChart(Ticker("AAPL"), Interval.`1Day`, Range.`1Year`)
    stock   <- client.charts.getStock(Ticker("AAPL"))
    targets <- client.analysts.getAnalystPriceTargets(Ticker("AAPL"))
    sector  <- client.sectors.getSectorData(SectorKey.Technology)
  } yield {
    chart.foreach(c => println(s"${c.quotes.size} data points"))
    stock.foreach(s => println(s"${s.longName}: ${s.regularMarketPrice}"))
    targets.foreach(t => println(s"Target: ${t.targetMean} (${t.numberOfAnalysts} analysts)"))
    sector.foreach(s => println(s"${s.name}: ${s.industryCount} industries"))
  }
}
```

## Documentation

| Guide | Description |
|-------|-------------|
| [Getting Started](docs/index.md) | Overview, client setup, API modules |
| [Charts & Quotes](docs/charts.md) | Historical data, fundamentals, dividends, splits |
| [Options](docs/options.md) | Option chains, expirations, contracts |
| [Holders](docs/holders.md) | Institutional, mutual fund, and insider data |
| [Financial Statements](docs/financials.md) | Income statements, balance sheets, cash flows |
| [Analyst Data](docs/analysts.md) | Price targets, recommendations, estimates |
| [Sector Data](docs/sectors.md) | Sector overview, industries, top ETFs/funds |
| [Industry Data](docs/industries.md) | Industry overview, top companies, performers, growth |
| [Market Data](docs/markets.md) | Region summary, market status, trending tickers |
| [Earnings Calendar](docs/calendars.md) | Market-wide and per-ticker earnings events |
| [Search & ISIN](docs/search.md) | Ticker search and ISIN lookup |
| [Screener](docs/screener.md) | Custom and predefined stock/fund screens |
| [Batch Operations](docs/batch-operations.md) | Multi-ticker parallel fetches |
| [Reference](docs/reference.md) | Intervals, ranges, frequencies, data models |

## Scala.js Setup

When using this library with Scala.js, install the `node-html-parser` npm package:

```bash
npm install node-html-parser
```

## License

See LICENSE file for details.
