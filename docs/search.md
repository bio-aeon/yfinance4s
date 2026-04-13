# Search and ISIN Lookup

## Search

Search for tickers, companies, and news:

```scala
clientResource.use { client =>
  client.search("Tesla").map { result =>
    result.quotes.foreach { q =>
      println(s"${q.symbol}: ${q.displayName} (${q.quoteType.getOrElse("N/A")})")
    }
    result.news.foreach { n =>
      println(s"${n.title} — ${n.publisher}")
    }
  }
}
```

The `search` method accepts optional parameters:

```scala
client.search(
  query = "Apple",
  maxResults = 10,        // default: 8
  newsCount = 5,          // default: 8
  enableFuzzyQuery = true // default: false, enables typo tolerance
)
```

## ISIN Lookup

Resolve an ISIN (International Securities Identification Number) to a Yahoo Finance ticker. The ISIN is validated against ISO 6166 format with Luhn check digit verification.

```scala
clientResource.use { client =>
  // Best match (primary listing)
  client.lookupByISIN("US0378331005").map {
    case Some(ticker) => println(s"Found: ${ticker.value}") // AAPL
    case None         => println("No match")
  }
}

clientResource.use { client =>
  // All exchange listings for an ISIN
  client.lookupAllByISIN("US0378331005").map { results =>
    results.foreach(q => println(s"${q.symbol} on ${q.exchangeDisplay.getOrElse("N/A")}"))
  }
}
```
