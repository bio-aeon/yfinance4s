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
  private val weekend = ZonedDateTime.of(2026, 7, 25, 0, 0, 0, 0, ZoneOffset.UTC)

  /** SPY's `meta` block as Yahoo served it before the 2026-10-26 session, for a chart of the given granularity. */
  private def spyMeta(granularity: String): String =
    s"""{"currency": "USD", "symbol": "SPY", "exchangeName": "PCX", "instrumentType": "ETF",
       |    "regularMarketTime": 1792785600, "gmtoffset": -14400, "timezone": "EDT",
       |    "exchangeTimezoneName": "America/New_York", "regularMarketPrice": 759.97,
       |    "dataGranularity": "$granularity", "range": ""}""".stripMargin

  /** SPY's hourly bars for the 2025-11-28 half-day as Yahoo serves them: no adjusted closes, no data at 17:30 UTC. */
  private val spyHalfDayHourly =
    s"""{"chart": {"result": [{
       |  "meta": ${spyMeta("1h")},
       |  "timestamp": [1764340200, 1764343800, 1764347400, 1764351000, 1764352800],
       |  "indicators": {
       |    "quote": [{"open": [680.86, 681.69, 682.16, null, 682.315], "high": [682.32, 682.94, 683.0, null, 683.45],
       |      "low": [680.5, 681.61, 681.8, null, 674.715], "close": [681.69, 682.15, 682.315, null, 683.11],
       |      "volume": [9771492, 8375543, 9614422, null, 0]}]
       |  }
       |}], "error": null}}""".stripMargin

  /** SPY's last two hourly bars for 2026-07-27 to 2026-07-31 as Yahoo served the window before the 2026-10-26 session,
    * followed by its latest-price row from 2026-10-23.
    */
  private val spyPastHourly =
    s"""{"chart": {"result": [{
       |  "meta": ${spyMeta("1h")},
       |  "timestamp": [1785522600, 1785526200, 1792785600],
       |  "indicators": {
       |    "quote": [{
       |      "open": [746.04, 747.49, 759.97],
       |      "high": [747.92, 748.895, 759.97],
       |      "low": [745.67, 746.34, 759.97],
       |      "close": [747.49, 746.81, 759.97],
       |      "volume": [7842744, 10315583, 0]
       |    }]
       |  }
       |}], "error": null}}""".stripMargin

  /** SPY's daily chart for the weekend from 2026-07-25 as Yahoo serves it: no `timestamp`, and quote objects that hold
    * no columns.
    */
  private val spyWeekendDaily =
    s"""{"chart": {"result": [{
       |  "meta": ${spyMeta("1d")},
       |  "indicators": {"quote": [{}], "adjclose": [{}]}
       |}], "error": null}}""".stripMargin

  /** SPY's hourly chart for the same weekend, which comes without the adjusted-close object. */
  private val spyWeekendHourly =
    s"""{"chart": {"result": [{
       |  "meta": ${spyMeta("1h")},
       |  "indicators": {"quote": [{}]}
       |}], "error": null}}""".stripMargin

  private val toyota = Ticker("7203.T")

  /** A moment in October 2026, in UTC. */
  private def tokyoAt(day: Int, hour: Int, minute: Int): ZonedDateTime =
    ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZoneOffset.UTC)

  /** Yahoo's `meta` block for 7203.T, reporting as current the regular session of the given day: 00:00 to 06:30 UTC. */
  private def tokyoMeta(granularity: String, range: String, latestPrint: ZonedDateTime, sessionDay: Int): String = {
    val (open, close) = (tokyoAt(sessionDay, 0, 0).toEpochSecond, tokyoAt(sessionDay, 6, 30).toEpochSecond)
    s"""{"currency": "JPY", "symbol": "7203.T", "exchangeName": "JPX", "instrumentType": "EQUITY",
       |    "regularMarketTime": ${latestPrint.toEpochSecond}, "gmtoffset": 32400, "timezone": "JST",
       |    "exchangeTimezoneName": "Asia/Tokyo", "dataGranularity": "$granularity", "range": "$range",
       |    "currentTradingPeriod": {"regular": {"timezone": "JST", "start": $open, "end": $close,
       |      "gmtoffset": 32400}}}""".stripMargin
  }

  /** 7203.T's daily chart for 2026-10-20 to 2026-10-26 as Yahoo serves it at 02:11 UTC on the 26th, mid-session; the
    * latest print and the session reported as current can be moved to stand for another moment.
    */
  private def tokyoDaily(latestPrint: ZonedDateTime = tokyoAt(26, 2, 11), sessionDay: Int = 26): String =
    s"""{"chart": {"result": [{
       |  "meta": ${tokyoMeta("1d", "5d", latestPrint, sessionDay)},
       |  "timestamp": [1792454400, 1792540800, 1792627200, 1792713600, 1792972800],
       |  "indicators": {
       |    "quote": [{
       |      "open": [3087.0, 3149.0, 3069.0, 3101.0, 3072.0],
       |      "high": [3112.0, 3149.0, 3116.0, 3119.0, 3137.0],
       |      "low": [3075.0, 3062.0, 3067.0, 3064.0, 3069.0],
       |      "close": [3102.0, 3094.0, 3084.0, 3105.0, 3126.0],
       |      "volume": [15718300, 19532800, 16992800, 23788700, 6991800]
       |    }],
       |    "adjclose": [{"adjclose": [3102.0, 3094.0, 3084.0, 3105.0, 3126.0]}]
       |  }
       |}], "error": null}}""".stripMargin

  /** 7203.T's hourly bars for the 2026-10-26 session, served mid-session at 02:13 UTC. */
  private val tokyoHourly =
    s"""{"chart": {"result": [{
       |  "meta": ${tokyoMeta("1h", "1d", latestPrint = tokyoAt(26, 2, 13), sessionDay = 26)},
       |  "timestamp": [1792972800, 1792976400, 1792980000],
       |  "indicators": {
       |    "quote": [{
       |      "open": [3072.0, 3120.0, 3122.0],
       |      "high": [3137.0, 3128.0, 3129.0],
       |      "low": [3069.0, 3114.0, 3122.0],
       |      "close": [3119.0, 3122.0, 3128.0],
       |      "volume": [4119400, 1491100, 266900]
       |    }]
       |  }
       |}], "error": null}}""".stripMargin

  private def barsInProgress(chart: ChartResult): List[ZonedDateTime] =
    chart.quotes.filter(_.inProgress).map(_.datetime)

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

  test("getChart marks the daily bar of a session still open as in progress") {
    chartsServing(tokyoDaily()).flatMap(_.getChart(toyota, Interval.`1Day`, Range.`5Days`)).map { chart =>
      assertEquals(chart.map(barsInProgress), Some(List(tokyoAt(26, 0, 0))))
    }
  }

  test("getChart marks no daily bar in progress once the session has closed") {
    chartsServing(tokyoDaily(latestPrint = tokyoAt(26, 6, 30)))
      .flatMap(_.getChart(toyota, Interval.`1Day`, Range.`5Days`))
      .map(chart => assertEquals(chart.map(barsInProgress), Some(Nil)))
  }

  test("getChart marks no daily bar in progress when Yahoo already reports the next session") {
    chartsServing(tokyoDaily(latestPrint = tokyoAt(26, 6, 30), sessionDay = 27))
      .flatMap(_.getChart(toyota, Interval.`1Day`, Range.`5Days`))
      .map(chart => assertEquals(chart.map(barsInProgress), Some(Nil)))
  }

  test("getChart marks no daily bar in progress for a window that ends before the open session") {
    chartsServing(tokyoDaily(latestPrint = tokyoAt(27, 1, 0), sessionDay = 27))
      .flatMap(_.getChart(toyota, Interval.`1Day`, tokyoAt(20, 0, 0), tokyoAt(27, 0, 0)))
      .map(chart => assertEquals(chart.map(barsInProgress), Some(Nil)))
  }

  test("getChart marks no hourly bar in progress") {
    chartsServing(tokyoHourly)
      .flatMap(_.getChart(toyota, Interval.`1Hour`, Range.`1Day`))
      .map(chart => assertEquals(chart.map(barsInProgress), Some(Nil)))
  }

  test("getChart leaves out the latest-price row Yahoo appends after a past window") {
    val lastTwoHours = List(18, 19).map(hour => ZonedDateTime.of(2026, 7, 31, hour, 30, 0, 0, ZoneOffset.UTC))
    chartsServing(spyPastHourly)
      .flatMap(_.getChart(spy, Interval.`1Hour`, since, until))
      .map(chart => assertEquals(chart.map(_.quotes.map(_.datetime)), Some(lastTwoHours)))
  }

  test("getChart returns a chart without quotes for a daily window without a session") {
    chartsServing(spyWeekendDaily)
      .flatMap(_.getChart(spy, Interval.`1Day`, weekend, weekend.plusDays(2)))
      .map(chart => assertEquals(chart, Some(ChartResult(quotes = Nil, currency = "USD"))))
  }

  test("getChart returns a chart without quotes for an hourly window without a session") {
    chartsServing(spyWeekendHourly)
      .flatMap(_.getChart(spy, Interval.`1Hour`, weekend, weekend.plusDays(2)))
      .map(chart => assertEquals(chart, Some(ChartResult(quotes = Nil, currency = "USD"))))
  }

  test("getChart with repair returns a chart without quotes for a window without a session") {
    chartsServing(spyWeekendDaily)
      .flatMap(_.getChart(spy, Interval.`1Day`, weekend, weekend.plusDays(2), PriceRepairConfig.Enabled))
      .map(chart => assertEquals(chart, Some(ChartResult(quotes = Nil, currency = "USD"))))
  }
}
