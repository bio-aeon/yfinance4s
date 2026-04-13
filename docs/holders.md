# Holders Data

The `holders` module provides institutional ownership, mutual fund holdings, and insider transaction data.

## Major Holders Breakdown

```scala
clientResource.use { client =>
  client.holders.getMajorHolders(Ticker("AAPL")).map {
    case Some(holders) =>
      println(f"Insiders: ${holders.insidersPercentHeld * 100}%.2f%%")
      println(f"Institutions: ${holders.institutionsPercentHeld * 100}%.2f%%")
      println(s"Institution count: ${holders.institutionsCount}")
    case None =>
      println("No holders data")
  }
}
```

## Institutional Holders

```scala
clientResource.use { client =>
  client.holders.getInstitutionalHolders(Ticker("MSFT")).flatMap { holders =>
    IO.println(s"Top ${holders.size} institutional holders:") *>
    holders.take(5).traverse_ { h =>
      IO.println(f"  ${h.organization}: ${h.percentHeld * 100}%.2f%% (${h.shares} shares)")
    }
  }
}
```

## Insider Transactions

```scala
clientResource.use { client =>
  client.holders.getInsiderTransactions(Ticker("GOOGL")).flatMap { transactions =>
    transactions.take(5).traverse_ { t =>
      val action = if (t.isPurchase) "bought" else "sold"
      IO.println(s"${t.filerName} $action ${t.shares} shares on ${t.transactionDate}")
    }
  }
}
```

## Comprehensive Holders Data

```scala
clientResource.use { client =>
  client.holders.getHoldersData(Ticker("AMZN")).map {
    case Some(data) =>
      println(s"Institutional holders: ${data.institutionalHolders.size}")
      println(s"Mutual fund holders: ${data.mutualFundHolders.size}")
      println(s"Insider transactions: ${data.insiderTransactions.size}")
      println(f"Net insider shares: ${data.netInsiderShares}%+d")
    case None =>
      println("No holders data")
  }
}
```
