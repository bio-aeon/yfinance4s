# Earnings Calendar

The `calendars` module exposes Yahoo Finance's calendar-style queries, currently covering earnings events. Two query shapes are supported: a market-wide window (rank upcoming earnings across all tickers by market cap, date range, and threshold) and a per-ticker timeline (historical and upcoming earnings for one symbol).

## Market-Wide Earnings Calendar

Returns `EarningsEvent`s within a date range, ordered by scheduled date ascending. Yahoo's upstream default ranks by market cap descending; results are re-sorted client-side so the earliest events appear first.

```scala
import java.time.{LocalDate, ZoneOffset}
import org.coinductive.yfinance4s.models.*

clientResource.use { client =>
  val today = LocalDate.now(ZoneOffset.UTC)
  client.calendars.getEarningsCalendar(start = today, end = today.plusDays(7)).map { events =>
    events.foreach { e =>
      val cap = e.marketCap.map(mc => f"$$${mc / 1_000_000_000d}%.1fB").getOrElse("N/A")
      val eps = e.epsEstimate.map(x => f"$x%.2f").getOrElse("-")
      println(f"${e.symbol.value}%-8s ${e.date} ${e.timing.value}%-3s  est=$eps  cap=$cap")
    }
  }
}
```

Defaults: `start = LocalDate.now()`, `end = start + 7 days`. For zone-independent queries pass `LocalDate.now(ZoneOffset.UTC)` explicitly.

### Calendar Config

`CalendarConfig` tunes pagination, size, sort, and the optional market-cap threshold:

```scala
val config = CalendarConfig(
  limit = 50,                          // 1..100, Yahoo-capped
  offset = 0,                          // pagination
  marketCap = Some(10_000_000_000d),   // only companies with >= $10B market cap
  sort = CalendarSort.ByDateAsc        // or CalendarSort.Default / ByDateDesc
)

clientResource.use { client =>
  client.calendars.getEarningsCalendar(
    start = LocalDate.now(ZoneOffset.UTC),
    end = LocalDate.now(ZoneOffset.UTC).plusDays(30),
    config = config
  )
}
```

Default config: `limit = 25`, `offset = 0`, no market-cap filter, sort by market cap descending.

### Event Helpers

`EarningsEvent` carries company metadata plus a `timing` modifier (`BeforeMarketOpen`, `AfterMarketClose`, `TimeNotSupplied`, `TimeAsSpecified`) and EPS fields where available:

```scala
events.foreach { e =>
  if (e.isBeforeMarketOpen) println(s"${e.symbol.value} reports pre-market")
  if (e.isAfterMarketClose) println(s"${e.symbol.value} reports post-close")
  e.isBeat.foreach(beat => println(s"${e.symbol.value} ${if (beat) "beat" else "missed"} consensus"))
}
```

Alternative orderings are available on the companion object:

```scala
val byMarketCap = events.sorted(EarningsEvent.byMarketCapDesc)
val byDateDesc  = events.sorted(EarningsEvent.byDateDesc)
```

## Per-Ticker Earnings Timeline

Returns a ticker's recent and upcoming earnings events as `EarningsDate`s, ordered by date descending (most recent first). Unlike the market-wide feed, per-ticker events include an explicit `eventType` (`EarningsCall`, `EarningsReport`, `StockholdersMeeting`).

```scala
clientResource.use { client =>
  client.calendars.getEarningsDates(Ticker("AAPL"), limit = 12).map { dates =>
    dates.foreach { d =>
      val est = d.epsEstimate.map(x => f"$x%.2f").getOrElse("-")
      val act = d.epsActual.map(x => f"$x%.2f").getOrElse("-")
      val srp = d.surprisePercent.map(x => f"$x%+.1f%%").getOrElse("-")
      println(f"${d.localDate} ${d.eventType}%-20s  est=$est  act=$act  surprise=$srp")
    }
  }
}
```

Defaults: `limit = 12`, `offset = 0`. Yahoo caps the limit at 100; use `offset` for pagination.

Tickers must be in Yahoo's canonical uppercase form (e.g., `"AAPL"`). Lowercase symbols are rejected upstream and return an empty list.

### Filtering by Event Type

```scala
clientResource.use { client =>
  client.calendars.getEarningsDates(Ticker("MSFT"), limit = 20).map { dates =>
    val reports = dates.filter(_.isReport)
    val calls = dates.filter(_.isCall)
    val pastBeats = dates.filter(d => d.isPast(ZonedDateTime.now()) && d.isBeat.contains(true))
    println(s"Reports: ${reports.size}, Calls: ${calls.size}, Past beats: ${pastBeats.size}")
  }
}
```
