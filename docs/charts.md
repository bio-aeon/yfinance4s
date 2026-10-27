# Charts, Quotes, and Corporate Actions

The `charts` module provides historical price data, current stock quotes, and corporate action history.

## Historical Chart Data

```scala
import io.github.coinductive.yfinance4s.models.{Interval, Range, Ticker}

clientResource.use { client =>
  client.charts.getChart(Ticker("AAPL"), Interval.`1Day`, Range.`1Year`).flatMap {
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

Yahoo occasionally reports a timestamp without any prices, for a day it has no data for. Such a row yields no quote, while a dividend or split on that day is still listed; a bar whose volume Yahoo leaves out reports a volume of `0`. A window without any trading session, such as a weekend or a holiday, yields a chart without quotes.

Yahoo sends no adjusted closes for intraday intervals, so on an intraday chart each quote's `adjclose` equals its `close`.

While a session is open, a daily chart ends with a bar for that session whose close is the latest price. That quote carries `inProgress = true`, so `chart.quotes.filterNot(_.inProgress)` keeps only sessions that have closed. Quotes of other intervals are never marked.

Yahoo also adds a row for its latest price to most charts, which is not a bar of the chart's interval. It is folded into the bar it falls in: an intraday chart's last bar closes at the latest price, or once the session has ended at its closing price, and a weekly or monthly bar takes in the latest session. A chart requested by dates leaves the row out when Yahoo stamps it outside the window.

## Date Range Queries

```scala
import java.time.ZonedDateTime

clientResource.use { client =>
  val since = ZonedDateTime.parse("2024-01-01T00:00:00Z")
  val until = ZonedDateTime.parse("2024-12-01T00:00:00Z")

  client.charts.getChart(Ticker("MSFT"), Interval.`1Day`, since, until)
}
```

## Price Repair

Yahoo occasionally reports bars in the wrong currency subunit (e.g. pence instead of pounds - exactly 100x off), either as sporadic outliers or as a whole block that switched units. Pass `repair` to detect and fix these in-flight:

```scala
import io.github.coinductive.yfinance4s.models.PriceRepairConfig

clientResource.use { client =>
  client.charts.getChart(Ticker("VOD.L"), Interval.`1Day`, Range.`5Years`, repair = PriceRepairConfig.Enabled).map {
    case Some(chart) =>
      val touched = chart.quotes.filter(_.repaired)
      println(s"${chart.quotes.size} bars, ${touched.size} repaired")
    case None => println("No data found")
  }
}
```

Date-range queries take `repair` through a dedicated overload:

```scala
clientResource.use { client =>
  val since = ZonedDateTime.parse("2020-01-01T00:00:00Z")
  val until = ZonedDateTime.parse("2024-12-31T00:00:00Z")

  client.charts.getChart(
    Ticker("VOD.L"),
    Interval.`1Day`,
    since,
    until,
    PriceRepairConfig.Custom(fix100xErrors = true, fixZeroes = false)
  )
}
```

Repair is opt-in (`Disabled` by default), applies to daily and intraday intervals (others are returned unrepaired), and is best-effort - it never fails the request. Repaired bars carry `repaired = true`, and a repaired chart's `dividends` reflect any correction; `getDividends` always reports Yahoo's raw amounts. See `PriceRepairConfig` for the full semantics.

## Currency Standardisation and Dividend FX

Yahoo quotes some international markets in currency subunits rather than major units:

| Yahoo currency | Standardised to | Factor |
|---|---|---|
| `GBp` (UK pence) | `GBP` | x0.01 |
| `ZAc` (South African cents) | `ZAR` | x0.01 |
| `ILA` (Israeli agora) | `ILS` | x0.01 |
| `KWF` (Kuwaiti fils) | `KWD` | x0.001 |

The same `repair` parameter standardises them, so a portfolio spanning several markets never mixes pence with pounds. Every chart reports its trading currency through `ChartResult.currency`, with or without repair:

```scala
clientResource.use { client =>
  client.charts.getChart(Ticker("VOD.L"), Interval.`1Day`, Range.`1Year`, repair = PriceRepairConfig.Enabled).map {
    case Some(chart) =>
      println(chart.currency)             // GBP, standardised from Yahoo's GBp
      println(chart.quotes.last.close)    // pounds, not pence
    case None => println("No data found")
  }
}
```

Unlike the error repairs, standardisation applies to every interval, and it does not mark bars `repaired` - it is a unit conversion, not a correction. Provenance is the currency label itself.

Some issuers pay dividends in a different currency than their share price. Yahoo labels those dividends, and `DividendEvent.currency` surfaces the label on every path. With `convertDividendFx` on, such dividends are converted into the price currency at the latest Yahoo FX rate (one small chart fetch per distinct dividend currency - none at all in the common case where no dividend is labelled). Conversion is best-effort: if the rate cannot be fetched, the dividend keeps its original amount and label, so a currency that still differs from `chart.currency` marks an unconverted amount.

```scala
// Standardise units but leave dividend FX alone.
PriceRepairConfig.Custom(
  fix100xErrors = true,
  fixZeroes = false,
  standardiseCurrency = true,
  convertDividendFx = false
)
```

## Stock Fundamentals

```scala
clientResource.use { client =>
  client.charts.getStock(Ticker("GOOGL")).map {
    case Some(stock) =>
      println(s"${stock.longName} (${stock.symbol})")
      println(s"Price: ${stock.currency} ${stock.regularMarketPrice}")
      println(s"Market Cap: ${stock.marketCap}")
      println(s"Sector: ${stock.sector.getOrElse("N/A")}")
      println(s"P/E Ratio: ${stock.trailingPE.getOrElse("N/A")}")
      println(s"Dividend Yield: ${stock.dividendYield.map(y => f"${y * 100}%.2f%%").getOrElse("N/A")}")
    case None =>
      println("No quote data")
  }
}
```

`getStock` raises `YFinanceError.TickerNotFound` for an unknown ticker, like the other endpoints, and returns `None` only when Yahoo has no quote data for a known symbol. Each call makes two requests to Yahoo - quote summary and fundamentals - both of which count against the client's rate limit.

## Dividends

```scala
clientResource.use { client =>
  client.charts.getDividends(Ticker("AAPL"), Interval.`1Day`, Range.`5Years`).map {
    case Some(dividends) =>
      dividends.foreach { div =>
        println(s"${div.exDate}: $${div.amount}")
      }
    case None => println("No dividend data")
  }
}
```

A dividend paid in a different currency than the share price carries Yahoo's label in `DividendEvent.currency` (absent means the trading currency). `getDividends` always reports raw amounts and labels; the converted view lives on a repaired chart's `dividends` (see the currency section above).

## Stock Splits

```scala
clientResource.use { client =>
  client.charts.getSplits(Ticker("TSLA"), Interval.`1Day`, Range.Max).map {
    case Some(splits) =>
      splits.foreach { split =>
        println(s"${split.exDate}: ${split.splitRatio} (${if (split.isForwardSplit) "forward" else "reverse"})")
      }
    case None => println("No split data")
  }
}
```

## Combined Corporate Actions

```scala
clientResource.use { client =>
  client.charts.getCorporateActions(Ticker("MSFT"), Interval.`1Day`, Range.`10Years`).map {
    case Some(actions) =>
      println(s"Dividends: ${actions.dividends.size}, Splits: ${actions.splits.size}")
    case None => println("No corporate actions")
  }
}
```
