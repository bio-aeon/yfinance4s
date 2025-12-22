# YFinance4s

Effectful Yahoo Finance client in the Scala programming language.

## Features

- **Historical Data**: Fetch OHLCV (Open, High, Low, Close, Volume) price data with configurable intervals and date ranges
- **Stock Fundamentals**: Retrieve comprehensive company data including financials, valuation ratios, and key statistics
- **Purely Functional**: Built on Cats Effect 3 with `Resource`-based lifecycle management
- **Cross-compiled**: Supports both Scala 2.13 and Scala 3
- **Retry Logic**: Built-in configurable retry policies for resilient API calls

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

## Data Models

### ChartResult

Contains historical OHLCV data:
- `datetime`: Timestamp of the data point
- `open`, `high`, `low`, `close`: Price data
- `volume`: Trading volume
- `adjclose`: Adjusted closing price

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

## License

See LICENSE file for details.
