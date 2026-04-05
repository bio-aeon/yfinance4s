# Analyst Data

The `analysts` module provides price targets, buy/hold/sell recommendations, earnings estimates, upgrade/downgrade history, and growth comparisons.

## Price Targets

```scala
clientResource.use { client =>
  client.analysts.getAnalystPriceTargets(Ticker("AAPL")).map {
    case Some(targets) =>
      println(s"Current Price: ${targets.currentPrice}")
      println(s"Target Range: ${targets.targetLow} - ${targets.targetHigh}")
      println(s"Mean Target: ${targets.targetMean} (${targets.numberOfAnalysts} analysts)")
      println(s"Recommendation: ${targets.recommendationKey}")
      println(f"Upside: ${targets.meanUpsidePercent}%.1f%%")
    case None =>
      println("No price targets available")
  }
}
```

## Recommendation Trends

```scala
clientResource.use { client =>
  client.analysts.getRecommendations(Ticker("MSFT")).flatMap { trends =>
    trends.headOption.traverse_ { rec =>
      IO.println(s"Period: ${rec.period}") *>
      IO.println(s"Strong Buy: ${rec.strongBuy}, Buy: ${rec.buy}, Hold: ${rec.hold}") *>
      IO.println(s"Sell: ${rec.sell}, Strong Sell: ${rec.strongSell}") *>
      IO.println(f"Bullish: ${rec.bullishPercent}%.1f%%")
    }
  }
}
```

## Upgrade/Downgrade History

```scala
clientResource.use { client =>
  client.analysts.getUpgradeDowngradeHistory(Ticker("GOOGL")).flatMap { history =>
    history.take(5).traverse_ { ud =>
      val action = if (ud.isUpgrade) "upgraded" else if (ud.isDowngrade) "downgraded" else "rated"
      IO.println(s"${ud.date}: ${ud.firm} $action to ${ud.toGrade}")
    }
  }
}
```

## Earnings Estimates

```scala
clientResource.use { client =>
  client.analysts.getEarningsEstimates(Ticker("NVDA")).flatMap { estimates =>
    estimates.headOption.traverse_ { est =>
      IO.println(s"Period: ${est.period}") *>
      IO.println(s"Average EPS Estimate: ${est.avg}") *>
      IO.println(s"Range: ${est.low} - ${est.high}")
    }
  }
}
```

## Comprehensive Analyst Data

Fetch all analyst data in a single call:

```scala
clientResource.use { client =>
  client.analysts.getAnalystData(Ticker("AAPL")).map {
    case Some(data) =>
      data.priceTargets.foreach(t => println(s"Mean Target: ${t.targetMean}"))
      data.currentRecommendation.foreach(r => println(s"Current: ${r.totalBullish} bullish, ${r.totalBearish} bearish"))
      println(s"Consecutive Beats: ${data.consecutiveBeats}")
      data.earningsBeatRate.foreach(rate => println(f"Beat Rate: $rate%.0f%%"))
    case None =>
      println("No analyst data")
  }
}
```
