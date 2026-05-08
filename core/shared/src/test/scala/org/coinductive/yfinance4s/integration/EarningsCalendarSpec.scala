package org.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}

import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.*

class EarningsCalendarSpec extends CatsEffectSuite {

  private val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  private val today: LocalDate = LocalDate.now(ZoneOffset.UTC)

  // --- getEarningsCalendar ---

  test("returns a non-empty earnings calendar over a 30-day window") {
    YFinanceClient.resource[IO](config).use { client =>
      client.calendars
        .getEarningsCalendar(start = today, end = today.plusDays(30))
        .map { events =>
          assert(events.nonEmpty, "earnings calendar should return results over a 30-day window")
          events.foreach { e =>
            assert(e.symbol.value.nonEmpty, s"symbol should be non-empty: $e")
          }
        }
    }
  }

  test("orders returned events by scheduled date ascending") {
    YFinanceClient.resource[IO](config).use { client =>
      client.calendars
        .getEarningsCalendar(start = today, end = today.plusDays(14), config = CalendarConfig(limit = 20))
        .map { events =>
          val starts = events.map(_.startDateTime)
          assertEquals(starts, starts.sorted, "events should be ordered by startDateTime ascending")
        }
    }
  }

  test("respects the configured limit") {
    YFinanceClient.resource[IO](config).use { client =>
      client.calendars
        .getEarningsCalendar(start = today, end = today.plusDays(30), config = CalendarConfig(limit = 5))
        .map { events =>
          assert(events.size <= 5, s"expected at most 5 events, got ${events.size}")
        }
    }
  }

  test("market-cap filter excludes companies below the threshold") {
    YFinanceClient.resource[IO](config).use { client =>
      val threshold = 100_000_000_000d
      client.calendars
        .getEarningsCalendar(
          start = today,
          end = today.plusDays(60),
          config = CalendarConfig(limit = 25, marketCap = Some(threshold))
        )
        .map { events =>
          events.foreach { e =>
            e.marketCap.foreach { mc =>
              assert(mc >= threshold.toLong, s"event ${e.symbol.value} market cap $mc below threshold $threshold")
            }
          }
        }
    }
  }

  // --- getEarningsDates ---

  test("returns a non-empty earnings timeline for Apple") {
    YFinanceClient.resource[IO](config).use { client =>
      client.calendars.getEarningsDates(Ticker("AAPL"), limit = 8).map { dates =>
        assert(dates.nonEmpty, "Apple should have at least 8 visible earnings events")
        dates.foreach { d =>
          assert(d.date.getYear >= 2000, s"suspicious date: ${d.date}")
        }
      }
    }
  }

  test("orders Apple's earnings by date descending") {
    YFinanceClient.resource[IO](config).use { client =>
      client.calendars.getEarningsDates(Ticker("AAPL"), limit = 8).map { dates =>
        val stamps = dates.map(_.date)
        assertEquals(stamps, stamps.sortWith(_.isAfter(_)))
      }
    }
  }

  test("includes at least one historical earnings report with a reported EPS for Apple") {
    YFinanceClient.resource[IO](config).use { client =>
      client.calendars.getEarningsDates(Ticker("AAPL"), limit = 12).map { dates =>
        val withActual = dates.filter(_.epsActual.isDefined)
        assert(withActual.nonEmpty, s"expected at least one historical report with an actual, got $dates")
      }
    }
  }

  test("respects the limit parameter for per-ticker queries") {
    YFinanceClient.resource[IO](config).use { client =>
      client.calendars.getEarningsDates(Ticker("AAPL"), limit = 5).map { dates =>
        assert(dates.size <= 5, s"expected at most 5 dates, got ${dates.size}")
      }
    }
  }

  test("returns an empty list for a ticker that doesn't exist") {
    YFinanceClient.resource[IO](config).use { client =>
      client.calendars.getEarningsDates(Ticker("_DEFINITELY_NOT_A_TICKER_"), limit = 5).map { dates =>
        assert(dates.isEmpty, s"expected no events for a non-existent ticker, got ${dates.size}")
      }
    }
  }

  test("offset skips the first page of Apple's earnings events") {
    YFinanceClient.resource[IO](config).use { client =>
      for {
        page0 <- client.calendars.getEarningsDates(Ticker("AAPL"), limit = 5, offset = 0)
        page1 <- client.calendars.getEarningsDates(Ticker("AAPL"), limit = 5, offset = 5)
      } yield {
        if (page0.size == 5 && page1.nonEmpty) {
          val overlap = page0.map(_.date).toSet intersect page1.map(_.date).toSet
          assert(overlap.isEmpty, s"page 0 and page 1 should be disjoint, overlap: $overlap")
        }
      }
    }
  }
}
