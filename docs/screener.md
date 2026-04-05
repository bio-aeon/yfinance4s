# Stock Screener

The `screener` module supports custom equity/fund screens and predefined screens (day gainers, most active, undervalued, etc.).

## Predefined Screens

```scala
clientResource.use { client =>
  client.screener.screenPredefined(PredefinedScreen.DayGainers).map { result =>
    println(s"${result.total} day gainers found")
    result.quotes.take(5).foreach { q =>
      println(s"${q.symbol}: ${q.displayName} ${q.regularMarketChangePercent.map(p => f"$p%+.1f%%").getOrElse("")}")
    }
  }
}
```

### Available Predefined Screens

**Equity screens:**
`DayGainers`, `DayLosers`, `MostActives`, `MostShortedStocks`, `AggressiveSmallCaps`, `SmallCapGainers`, `GrowthTechnologyStocks`, `UndervaluedGrowthStocks`, `UndervaluedLargeCaps`

**Fund screens:**
`ConservativeForeignFunds`, `HighYieldBond`, `PortfolioAnchors`, `SolidLargeGrowthFunds`, `SolidMidcapGrowthFunds`, `TopMutualFunds`

## Custom Equity Screens

Build queries using the `EquityQuery` DSL:

```scala
clientResource.use { client =>
  val query = EquityQuery.and(
    EquityQuery.gt("percentchange", 3),
    EquityQuery.eq("region", "us"),
    EquityQuery.gte("intradaymarketcap", 2000000000L)
  )
  client.screener.screenEquities(query).map { result =>
    result.quotes.foreach { q =>
      println(s"${q.symbol}: ${q.regularMarketPrice.getOrElse("N/A")}")
    }
  }
}
```

### Query Operators

| Method | Description |
|--------|-------------|
| `EquityQuery.eq(field, value)` | Equals |
| `EquityQuery.gt(field, value)` | Greater than |
| `EquityQuery.gte(field, value)` | Greater than or equal |
| `EquityQuery.lt(field, value)` | Less than |
| `EquityQuery.lte(field, value)` | Less than or equal |
| `EquityQuery.between(field, low, high)` | Range filter |
| `EquityQuery.isIn(field, values)` | Set membership |
| `EquityQuery.and(queries*)` | All conditions must match |
| `EquityQuery.or(queries*)` | Any condition matches |

## Custom Fund Screens

Use `FundQuery` (same API as `EquityQuery`) to screen mutual funds:

```scala
client.screener.screenFunds(FundQuery.gt("performance.trailingReturns.ytd", 10))
```

## Configuration

Control sorting, pagination, and result count via `ScreenerConfig`:

```scala
val config = ScreenerConfig(
  sortField = "percentchange",
  sortOrder = SortOrder.Desc,
  offset = 0,
  count = 50  // max 250
)
client.screener.screenEquities(query, config)
```
