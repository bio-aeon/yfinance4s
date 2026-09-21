package io.github.coinductive.yfinance4s.unit

import munit.FunSuite
import io.github.coinductive.yfinance4s.models.*
import io.github.coinductive.yfinance4s.models.internal.{ChartMetaRaw, TradingPeriodRaw, TradingPeriodsRaw}

import java.time.{Instant, ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.*

class HistoryMetadataSpec extends FunSuite {

  private val regularStart = 1699968600L
  private val regularEnd = 1699992000L
  private val firstTrade = 345479400L
  private val lastPrint = 1700000000L

  private def tradingPeriod(offset: Long): TradingPeriodRaw =
    TradingPeriodRaw(timezone = "EDT", start = regularStart + offset, end = regularEnd + offset, gmtoffset = -14400L)

  private val fullMeta: ChartMetaRaw = ChartMetaRaw(
    currency = "USD",
    symbol = "AAPL",
    exchangeName = "NMS",
    fullExchangeName = Some("NasdaqGS"),
    instrumentType = "EQUITY",
    firstTradeDate = Some(firstTrade),
    regularMarketTime = Some(lastPrint),
    gmtoffset = -14400L,
    timezone = "EDT",
    exchangeTimezoneName = "America/New_York",
    regularMarketPrice = Some(195.89),
    chartPreviousClose = Some(192.53),
    priceHint = Some(2),
    currentTradingPeriod = Some(
      TradingPeriodsRaw(
        pre = Some(tradingPeriod(-19800L)),
        regular = Some(tradingPeriod(0L)),
        post = Some(tradingPeriod(14400L))
      )
    ),
    dataGranularity = "1d",
    range = "1mo",
    validRanges = Some(List("1d", "5d", "1mo", "1y")),
    hasPrePostMarketData = Some(true)
  )

  private def parse(raw: ChartMetaRaw): HistoryMetadata =
    HistoryMetadata.fromRaw(raw).fold(e => fail(s"expected Right, got Left($e)"), identity)

  private def utc(epochSeconds: Long): ZonedDateTime =
    ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC)

  test("maps a full meta payload to typed history metadata") {
    val md = parse(fullMeta)
    assertEquals(md.symbol, "AAPL")
    assertEquals(md.exchangeTimezoneName, "America/New_York")
    assertEquals(md.timezoneShortName, "EDT")
    assertEquals(md.instrumentType, InstrumentType.Equity)
    assertEquals(md.currency, "USD")
    assertEquals(md.gmtOffset, (-14400).seconds)
    assertEquals(md.firstTradeDate, Some(utc(firstTrade)))
    assertEquals(md.regularMarketTime, Some(utc(lastPrint)))
    assertEquals(md.priceHint, Some(2))
    assertEquals(md.currentTradingPeriod.map(_.regular.start), Some(utc(regularStart)))
    assertEquals(md.currentTradingPeriod.flatMap(_.pre).map(_.start), Some(utc(regularStart - 19800L)))
  }

  test("drops validRanges tokens the Range enum does not recognise") {
    val md = parse(fullMeta.copy(validRanges = Some(List("1d", "5d", "1mo", "1y", "frobnicate"))))
    assertEquals(md.validRanges, List(Range.`1Day`, Range.`5Days`, Range.`1Month`, Range.`1Year`))
  }

  test("returns Left when a present trading period block has no regular session") {
    val result = HistoryMetadata.fromRaw(
      fullMeta.copy(currentTradingPeriod =
        Some(TradingPeriodsRaw(pre = Some(tradingPeriod(0L)), regular = None, post = Some(tradingPeriod(0L))))
      )
    )
    assert(result.isLeft, s"expected Left, got $result")
  }

  test("omits the trading period block when Yahoo omits currentTradingPeriod") {
    val md = parse(fullMeta.copy(currentTradingPeriod = None))
    assertEquals(md.currentTradingPeriod, None)
    assertEquals(md.currency, "USD")
  }

  test("regularMarketTimeAtExchange re-labels the regular market time at the exchange offset preserving the instant") {
    val md = parse(fullMeta)
    assertEquals(md.regularMarketTimeAtExchange.map(_.toInstant), md.regularMarketTime.map(_.toInstant))
    assertEquals(md.regularMarketTimeAtExchange.map(_.getOffset), Some(ZoneOffset.ofTotalSeconds(-14400)))
  }

  test("isLikelyDelisted flags an existing instrument with a stale or absent last print") {
    val asOf = Instant.ofEpochSecond(lastPrint)
    val threshold = 14.days
    val base = parse(fullMeta)

    val staleExisting =
      base.copy(firstTradeDate = Some(utc(firstTrade)), regularMarketTime = Some(utc(lastPrint - 30 * 86400L)))
    val freshExisting =
      base.copy(firstTradeDate = Some(utc(firstTrade)), regularMarketTime = Some(utc(lastPrint - 86400L)))
    val neverExisted = base.copy(firstTradeDate = None, regularMarketTime = None)
    val existingNoPrint = base.copy(firstTradeDate = Some(utc(firstTrade)), regularMarketTime = None)

    assert(staleExisting.isLikelyDelisted(asOf, threshold), "existing + stale should be flagged")
    assert(!freshExisting.isLikelyDelisted(asOf, threshold), "existing + fresh should not be flagged")
    assert(!neverExisted.isLikelyDelisted(asOf, threshold), "never-existed should not be flagged")
    assert(existingNoPrint.isLikelyDelisted(asOf, threshold), "existing + no print should be flagged")
  }
}
