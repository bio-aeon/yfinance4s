# Options Data

The `options` module provides option chain data, expiration dates, and contract details.

## Expiration Dates

```scala
clientResource.use { client =>
  client.options.getOptionExpirations(Ticker("SPY")).flatMap {
    case Some(expirations) =>
      IO.println(s"Available expirations: ${expirations.take(5).mkString(", ")}...")
    case None =>
      IO.println("No options available")
  }
}
```

## Option Chain for a Specific Expiration

```scala
clientResource.use { client =>
  client.options.getOptionChain(Ticker("AAPL"), LocalDate.of(2025, 1, 17)).map {
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
```

## Full Option Chain (All Expirations)

```scala
clientResource.use { client =>
  client.options.getFullOptionChain(Ticker("NVDA")).map {
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
