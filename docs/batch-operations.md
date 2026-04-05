# Batch Operations

## Tickers Wrapper

The `Tickers` wrapper provides a fluent API for fetching data across multiple tickers in parallel.

```scala
import cats.data.NonEmptyList

clientResource.use { client =>
  val tickers = Tickers.of[IO](client, "AAPL", "MSFT", "GOOGL")

  // Fail-fast: raises on first failure
  tickers.history(Interval.`1Day`, Range.`1Month`).map { results =>
    results.foreach { case (ticker, chart) =>
      println(s"${ticker.value}: ${chart.quotes.size} data points")
    }
  }
}
```

### Error-Tolerant Mode

```scala
clientResource.use { client =>
  val tickers = Tickers.of[IO](client, "AAPL", "INVALIDTICKER")

  tickers.attemptHistory(Interval.`1Day`, Range.`1Month`).map { results =>
    results.foreach {
      case (ticker, Right(chart)) => println(s"${ticker.value}: ${chart.quotes.size} quotes")
      case (ticker, Left(err))    => println(s"${ticker.value}: failed - ${err.getMessage}")
    }
  }
}
```

### Builder API

```scala
clientResource.use { client =>
  val tickers = Tickers
    .single[IO](client, Ticker("AAPL"))
    .add(Ticker("MSFT"))
    .add(Ticker("GOOGL"))
    .withParallelism(2)

  // All data methods are available: info, financials, dividends, splits,
  // corporateActions, holdersData, analystData, optionExpirations
  for {
    quotes     <- tickers.info
    financials <- tickers.financials()
    analyst    <- tickers.analystData
  } yield {
    quotes.foreach { case (t, stock) => println(s"${t.value}: ${stock.regularMarketPrice}") }
  }
}
```

## Multi-Ticker Downloads

The client also provides lower-level `download*` methods using `NonEmptyList`:

```scala
import cats.data.NonEmptyList

clientResource.use { client =>
  val tickers = NonEmptyList.of(Ticker("AAPL"), Ticker("MSFT"), Ticker("GOOGL"))

  client.downloadCharts(tickers, Interval.`1Day`, Range.`1Month`, parallelism = 4).map { results =>
    results.foreach { case (ticker, chart) =>
      println(s"${ticker.value}: ${chart.quotes.size} data points")
    }
  }
}

clientResource.use { client =>
  val tickers = NonEmptyList.of(Ticker("AAPL"), Ticker("MSFT"))

  for {
    stocks     <- client.downloadStocks(tickers)
    financials <- client.downloadFinancialStatements(tickers, Frequency.Yearly)
  } yield {
    stocks.foreach { case (t, s) => println(s"${t.value}: ${s.regularMarketPrice}") }
  }
}
```
