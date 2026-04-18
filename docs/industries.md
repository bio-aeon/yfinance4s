# Industry Data

The `industries` module exposes Yahoo Finance's industry-level data: overview statistics, parent sector lookup, top companies, top performers, top growth companies, and research reports. Industries are the leaf nodes beneath [sectors](./sectors.md) in Yahoo's taxonomy.

## Industry Overview

```scala
clientResource.use { client =>
  client.industries.getIndustryData(IndustryKey("semiconductors")).map { data =>
    println(s"${data.name} (parent sector: ${data.sectorName})")
    data.overview.foreach { o =>
      println(s"Companies: ${o.companiesCount.getOrElse("N/A")}")
      println(s"Market Cap: ${o.marketCap.getOrElse("N/A")}")
      println(f"Market Weight: ${o.marketWeightPercent.getOrElse(0.0)}%.1f%%")
    }
  }
}
```

## Top Companies

```scala
clientResource.use { client =>
  client.industries.getTopCompanies(IndustryKey("semiconductors")).map { companies =>
    companies.foreach { c =>
      println(s"${c.symbol}: ${c.name.getOrElse("N/A")} (${c.rating.getOrElse("N/A")})")
    }
  }
}
```

## Top Performing Companies

Top performing companies are sorted by YTD return descending, with helpers for computing implied upside from analyst target prices.

```scala
clientResource.use { client =>
  client.industries.getTopPerformingCompanies(IndustryKey("semiconductors")).map { performers =>
    performers.foreach { p =>
      val ytd = p.ytdReturnPercent.map(y => f"$y%.1f%%").getOrElse("N/A")
      val upside = p.impliedUpsidePercent.map(u => f"$u%.1f%%").getOrElse("N/A")
      println(s"${p.symbol}: YTD $ytd, implied upside $upside")
    }
  }
}
```

## Top Growth Companies

```scala
clientResource.use { client =>
  client.industries.getTopGrowthCompanies(IndustryKey("biotechnology")).map { growers =>
    growers.foreach { g =>
      val growth = g.growthEstimatePercent.map(x => f"$x%.1f%%").getOrElse("N/A")
      println(s"${g.symbol}: growth estimate $growth")
    }
  }
}
```

## Parent Sector Lookup

Every industry belongs to a sector. `getSectorInfo` returns the parent sector's key and name, and `toSectorKey` round-trips to a `SectorKey`:

```scala
clientResource.use { client =>
  client.industries.getSectorInfo(IndustryKey("semiconductors")).map { info =>
    println(s"${info.sectorName} (${info.sectorKey})")
    val parent: SectorKey = info.toSectorKey
  }
}
```

## Discovering Industry Keys

Yahoo Finance exposes ~150+ industry keys across 11 sectors. Rather than hardcoding them, discover keys dynamically via `sectors.getIndustries`:

```scala
clientResource.use { client =>
  for {
    industries <- client.sectors.getIndustries(SectorKey.Technology)
    _ = industries.foreach(i => println(s"${i.key}: ${i.name}"))
    data <- client.industries.getIndustryData(IndustryKey(industries.head.key))
  } yield data
}
```

Any string can be wrapped in `IndustryKey`. If Yahoo doesn't recognise the key, the HTTP request raises in `F`.
