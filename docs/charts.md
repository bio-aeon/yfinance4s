# Charts, Quotes, and Corporate Actions

The `charts` module provides historical price data, current stock quotes, and corporate action history.

## Historical Chart Data

```scala
import org.coinductive.yfinance4s.models.{Interval, Range, Ticker}

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

## Date Range Queries

```scala
import java.time.ZonedDateTime

clientResource.use { client =>
  val since = ZonedDateTime.parse("2024-01-01T00:00:00Z")
  val until = ZonedDateTime.parse("2024-12-01T00:00:00Z")

  client.charts.getChart(Ticker("MSFT"), Interval.`1Day`, since, until)
}
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
      println("Stock not found")
  }
}
```

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
