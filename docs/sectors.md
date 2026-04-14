# Sector Data

The `sectors` module provides sector-level information for all 11 GICS sectors: overview statistics, top ETFs, mutual funds, industries, and top companies.

## Sector Overview

```scala
clientResource.use { client =>
  client.sectors.getSectorData(SectorKey.Technology).map {
    case Some(data) =>
      println(s"${data.name} sector")
      data.overview.foreach { o =>
        println(s"Companies: ${o.companiesCount.getOrElse("N/A")}")
        println(s"Market Cap: ${o.marketCap.getOrElse("N/A")}")
        println(f"Market Weight: ${o.marketWeightPercent.getOrElse(0.0)}%.1f%%")
      }
      println(s"Industries: ${data.industryCount}")
      println(s"Top ETFs: ${data.topETFs.map(_.symbol).mkString(", ")}")
    case None =>
      println("No sector data")
  }
}
```

## Industries Within a Sector

```scala
clientResource.use { client =>
  client.sectors.getIndustries(SectorKey.Healthcare).map { industries =>
    industries.foreach { i =>
      println(s"${i.name}: ${i.marketWeightPercent.map(w => f"$w%.1f%%").getOrElse("N/A")}")
    }
  }
}
```

For per-industry data (overview, top performers, top growth companies), use the `industries` algebra - see [industries.md](./industries.md).

## Top Companies

```scala
clientResource.use { client =>
  client.sectors.getTopCompanies(SectorKey.Energy).map { companies =>
    companies.foreach { c =>
      println(s"${c.symbol}: ${c.name.getOrElse("N/A")} (${c.rating.getOrElse("N/A")})")
    }
  }
}
```

## Available Sector Keys

All 11 GICS sectors are available as predefined constants on `SectorKey`:

| Constant | API Key |
|----------|---------|
| `SectorKey.BasicMaterials` | `basic-materials` |
| `SectorKey.CommunicationServices` | `communication-services` |
| `SectorKey.ConsumerCyclical` | `consumer-cyclical` |
| `SectorKey.ConsumerDefensive` | `consumer-defensive` |
| `SectorKey.Energy` | `energy` |
| `SectorKey.FinancialServices` | `financial-services` |
| `SectorKey.Healthcare` | `healthcare` |
| `SectorKey.Industrials` | `industrials` |
| `SectorKey.RealEstate` | `real-estate` |
| `SectorKey.Technology` | `technology` |
| `SectorKey.Utilities` | `utilities` |

Custom keys are also supported: `SectorKey("my-custom-sector")`.
