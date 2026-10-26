package io.github.coinductive.yfinance4s.unit

import cats.effect.IO
import munit.CatsEffectSuite
import io.github.coinductive.yfinance4s.{Charts, RateLimiter, YFinanceAuth, YFinanceGateway}
import io.github.coinductive.yfinance4s.models.*
import sttp.client3.Response
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.client3.testing.SttpBackendStub

import java.time.{ZoneOffset, ZonedDateTime}

class ChartsSpec extends CatsEffectSuite {

  private val london = Ticker("VOD.L")
  private val since = ZonedDateTime.of(2026, 7, 27, 0, 0, 0, 0, ZoneOffset.UTC)
  private val until = ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC)
  private val emptyDayEpoch = 1785394800L

  /** The 07:00 UTC session open Yahoo stamps on a London daily bar, for a day in July 2026. */
  private def londonDay(day: Int): ZonedDateTime = ZonedDateTime.of(2026, 7, day, 7, 0, 0, 0, ZoneOffset.UTC)

  /** The Charts algebra over an HTTP stub that answers every request, the gateway's and auth's alike, with the given
    * Yahoo response body.
    */
  private def chartsServing(body: String): IO[Charts[IO]] = {
    val backend = SttpBackendStub[IO, Any](new CatsMonadAsyncError[IO]).whenAnyRequest
      .thenRespond(Response.ok[Either[String, String]](Right(body)))
    val gateway = YFinanceGateway[IO](retries = 0, sttpBackend = backend, rateLimiter = RateLimiter.noop[IO])
    YFinanceAuth[IO](retries = 0, sttpBackend = backend, rateLimiter = RateLimiter.noop[IO]).map(Charts[IO](gateway, _))
  }

  /** VOD.L's daily chart for 2026-07-27 to 2026-07-31 as Yahoo serves it, with no data for 2026-07-30. */
  private def londonDaily(
      volumes: String = "285941088, 125127547, 52610345, null, 64799158",
      events: Option[String] = None
  ): String =
    s"""{"chart": {"result": [{
       |  "meta": {"currency": "GBp", "symbol": "VOD.L", "exchangeName": "LSE", "instrumentType": "EQUITY",
       |    "regularMarketTime": 1792770686, "gmtoffset": 0, "timezone": "GMT", "exchangeTimezoneName": "Europe/London",
       |    "regularMarketPrice": 118.2, "dataGranularity": "1d", "range": ""},
       |  "timestamp": [1785135600, 1785222000, 1785308400, $emptyDayEpoch, 1785481200],
       |  "indicators": {
       |    "quote": [{"open": [118.05, 120.2, 123.55, null, 119.4], "high": [121.25, 123.75, 123.8, null, 121.55],
       |      "low": [117.2, 119.05, 121.276, null, 115.873], "close": [120.15, 123.25, 122.05, null, 117.85],
       |      "volume": [$volumes]}],
       |    "adjclose": [{"adjclose": [120.15, 123.25, 122.05, null, 117.85]}]
       |  }${events.fold("")(e => s""", "events": $e""")}
       |}], "error": null}}""".stripMargin

  /** One unlabelled dividend, in pence, on the day Yahoo reports no data for. */
  private val dividendOnEmptyDay =
    s"""{"dividends": {"$emptyDayEpoch": {"amount": 2.25, "date": $emptyDayEpoch}}}"""

  private val spy = Ticker("SPY")
  private val halfDay = ZonedDateTime.of(2025, 11, 28, 0, 0, 0, 0, ZoneOffset.UTC)

  /** SPY's hourly bars for the 2025-11-28 half-day as Yahoo serves them: no adjusted closes, no data at 17:30 UTC. */
  private val spyHalfDayHourly =
    """{"chart": {"result": [{
      |  "meta": {"currency": "USD", "symbol": "SPY", "exchangeName": "PCX", "instrumentType": "ETF",
      |    "regularMarketTime": 1792785600, "gmtoffset": -14400, "timezone": "EDT",
      |    "exchangeTimezoneName": "America/New_York", "regularMarketPrice": 759.97, "dataGranularity": "1h", "range": ""},
      |  "timestamp": [1764340200, 1764343800, 1764347400, 1764351000, 1764352800],
      |  "indicators": {
      |    "quote": [{"open": [680.86, 681.69, 682.16, null, 682.315], "high": [682.32, 682.94, 683.0, null, 683.45],
      |      "low": [680.5, 681.61, 681.8, null, 674.715], "close": [681.69, 682.15, 682.315, null, 683.11],
      |      "volume": [9771492, 8375543, 9614422, null, 0]}]
      |  }
      |}], "error": null}}""".stripMargin

  test("getChart leaves out a row Yahoo reports without prices") {
    chartsServing(londonDaily()).flatMap(_.getChart(london, Interval.`1Day`, since, until)).map { chart =>
      val quotes = chart.getOrElse(fail("expected chart data")).quotes
      assertEquals(quotes.map(_.datetime), List(27, 28, 29, 31).map(londonDay))
      assertEquals(
        quotes.last,
        ChartResult.Quote(
          datetime = londonDay(31),
          close = 117.85,
          open = 119.4,
          volume = 64799158L,
          high = 121.55,
          low = 115.873,
          adjclose = 117.85
        )
      )
    }
  }

  test("getChart with repair leaves out the same rows as without it") {
    chartsServing(londonDaily()).flatMap { charts =>
      for {
        raw <- charts.getChart(london, Interval.`1Day`, since, until)
        repaired <- charts.getChart(london, Interval.`1Day`, since, until, PriceRepairConfig.Enabled)
      } yield assertEquals(repaired.map(_.quotes.map(_.datetime)), raw.map(_.quotes.map(_.datetime)))
    }
  }

  test("getChart reports zero volume where Yahoo reports none") {
    val body = londonDaily(volumes = "285941088, 125127547, 52610345, null, null")
    chartsServing(body).flatMap(_.getChart(london, Interval.`1Day`, since, until)).map { chart =>
      assertEquals(chart.flatMap(_.quotes.lastOption).map(_.volume), Some(0L))
    }
  }

  test("getChart with repair standardises a dividend on a row Yahoo reports without prices") {
    chartsServing(londonDaily(events = Some(dividendOnEmptyDay)))
      .flatMap(_.getChart(london, Interval.`1Day`, since, until, PriceRepairConfig.Enabled))
      .map { chart =>
        val dividends = chart.getOrElse(fail("expected chart data")).dividends
        assertEquals(dividends.map(_.exDate), List(londonDay(30)))
        val amount = dividends.head.amount
        assert(math.abs(amount - 0.0225) < 1e-9, s"expected the dividend in pounds, got $amount")
      }
  }

  test("getDividends returns the dividend on a row Yahoo reports without prices") {
    chartsServing(londonDaily(events = Some(dividendOnEmptyDay)))
      .flatMap(_.getDividends(london, Interval.`1Day`, since, until))
      .map(dividends => assertEquals(dividends, Some(List(DividendEvent(londonDay(30), 2.25)))))
  }

  test("getChart reports an hourly bar's close as its adjusted close") {
    chartsServing(spyHalfDayHourly)
      .flatMap(_.getChart(spy, Interval.`1Hour`, halfDay, halfDay.plusDays(1)))
      .map(chart => assertEquals(chart.map(_.quotes.map(_.adjclose)), Some(List(681.69, 682.15, 682.315, 683.11))))
  }
}
