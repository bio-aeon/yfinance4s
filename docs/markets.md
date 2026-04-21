# Market Data

The `markets` module exposes Yahoo Finance's region-level market data: headline index summaries, trading status (open/close times, timezone), and trending tickers. It sits alongside [sectors](./sectors.md) and [industries](./industries.md) as one of the domain-level aggregate queries.

## Region Keys

`MarketRegion` is a simple newtype around a Yahoo region code (uppercase ISO-3166 alpha-2). There are no curated constants - any code Yahoo accepts works identically. Common values: `US`, `GB`, `CA`, `DE`, `FR`, `IT`, `ES`, `AU`, `JP`, `HK`, `IN`, `BR`, `MX`.

```scala
import org.coinductive.yfinance4s.models.*

val US = MarketRegion("US")
val JP = MarketRegion("JP")
```

## Market Summary

Returns the region's headline indices and instruments as a list of `MarketIndexQuote`s.

```scala
clientResource.use { client =>
  client.markets.getSummary(MarketRegion("US")).map { summary =>
    summary.quotes.foreach { q =>
      val pct = q.regularMarketChangePercentAsPercent.getOrElse(0.0)
      val price = q.regularMarketPrice.getOrElse(0.0)
      println(f"${q.symbol}%-8s ${q.shortName.getOrElse("")}%-30s $price%.2f ($pct%+.2f%%)")
    }
  }
}
```

Helpers on `MarketSummary`:

```scala
summary.findBySymbol("^GSPC")          // Option[MarketIndexQuote] - exact match
summary.findByExchange("SNP")          // Option[MarketIndexQuote] - case-insensitive
summary.largestGainer                  // Option[MarketIndexQuote] - highest +%
summary.largestLoser                   // Option[MarketIndexQuote] - lowest -%
summary.movers(threshold = 0.02)       // List of quotes with |change| ≥ 2%
```

## Market Status

Returns trading status for a region. `status`, `open`, `close`, and `timezone` are required (Yahoo guarantees them when the endpoint responds); `message` and `yfitMarketState` are optional.

```scala
clientResource.use { client =>
  client.markets.getStatus(MarketRegion("US")).map { status =>
    println(s"State: ${status.status}")
    println(s"Open:  ${status.open}")
    println(s"Close: ${status.close}")
    println(s"TZ:    ${status.timezone.short} (${status.timezone.gmtOffset})")
    if (status.isOpen) println("Market is OPEN")
    else println("Market is CLOSED")
  }
}
```

`status.sessionDuration` returns the length of the regular session as a `FiniteDuration`.

## Trending Tickers

The region's most-searched symbols. Each entry is a bare `TrendingTicker(symbol)`; use `.toTicker` to convert to a `Ticker` for downstream queries.

```scala
clientResource.use { client =>
  for {
    trending <- client.markets.getTrending(MarketRegion("US"), count = 10)
    _ = trending.foreach(t => println(t.symbol))
    // Deep-dive on one of them via charts/stocks:
    stock <- client.charts.getStock(trending.head.toTicker)
  } yield stock
}
```

Yahoo caps the count server-side; the returned list may be shorter than requested.

## Market Snapshot

Convenience aggregate that runs all three queries sequentially for a single region.

```scala
clientResource.use { client =>
  client.markets.getMarketSnapshot(MarketRegion("US")).map { snap =>
    println(s"Market: ${if (snap.isOpen) "OPEN" else "CLOSED"}")
    println(s"Top mover: ${snap.summary.largestGainer.map(_.symbol).getOrElse("-")}")
    println(s"Trending:  ${snap.trending.map(_.symbol).mkString(", ")}")
  }
}
```

For parallel execution, call the individual methods via `parTraverseN` yourself.

## Common Symbols by Region

For orientation only - not encoded in the library:

| Region | Indices |
|---|---|
| `US` | `^GSPC` (S&P 500), `^DJI` (Dow), `^IXIC` (Nasdaq), `^RUT`, `^VIX` |
| `GB` | `^FTSE`, `^FTMC`, `^FTAS` |
| `DE` | `^GDAXI`, `^TECDAX` |
| `JP` | `^N225`, `^N300` |

For historical OHLCV on any of these symbols, use [`client.charts.getChart(Ticker("^GSPC"), interval, range)`](./charts.md).
