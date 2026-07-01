package org.coinductive.yfinance4s.models

import org.coinductive.yfinance4s.models.internal.{ChartMetaRaw, TradingPeriodRaw, TradingPeriodsRaw}

import java.time.{Instant, ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.{DurationLong, FiniteDuration}

/** A single trading-session window (regular, pre-market, or post-market) for an instrument's current day.
  *
  * @param start
  *   Session start, in UTC. Convert with the exchange offset for local wall-clock time.
  * @param end
  *   Session end, in UTC.
  * @param gmtOffset
  *   The exchange's offset from UTC at the time of this session.
  * @param timezoneShortName
  *   Yahoo's short timezone label for the session (e.g. "EDT").
  */
final case class TradingPeriod(
    start: ZonedDateTime,
    end: ZonedDateTime,
    gmtOffset: FiniteDuration,
    timezoneShortName: String
)

/** The current day's trading-session windows. `regular` is always present when Yahoo emits a trading-period block;
  * `pre` and `post` are present only for instruments with extended-hours sessions.
  */
final case class TradingPeriods(
    regular: TradingPeriod,
    pre: Option[TradingPeriod],
    post: Option[TradingPeriod]
)

/** Instrument-level metadata returned by Yahoo's chart endpoint alongside price bars.
  *
  * Returned by [[org.coinductive.yfinance4s.Charts.getHistoryMetadata]]. Timestamps (`firstTradeDate`,
  * `regularMarketTime`, trading-period bounds) are in UTC; localise via `exchangeTimezoneName` / `gmtOffset` when
  * wall-clock exchange time is needed (see [[regularMarketTimeAtExchange]]).
  *
  * @param symbol
  *   The ticker as Yahoo echoes it.
  * @param exchangeName
  *   Yahoo's exchange code (e.g. "NMS").
  * @param fullExchangeName
  *   Human-readable exchange name (e.g. "NasdaqGS"), where Yahoo supplies it.
  * @param instrumentType
  *   The kind of instrument (equity, ETF, index, ...). See [[InstrumentType]].
  * @param currency
  *   ISO currency code of the trading price (e.g. "USD"). Structurally guaranteed by Yahoo's `meta`.
  * @param exchangeTimezoneName
  *   The exchange's IANA timezone name (e.g. "America/New_York"). Kept as the raw string (matching
  *   [[MarketIndexQuote.exchangeTimezoneName]]); resolve to a `java.time.ZoneId` at the call site where a tz database
  *   is available.
  * @param timezoneShortName
  *   Yahoo's short timezone label (e.g. "EDT").
  * @param gmtOffset
  *   The exchange's current offset from UTC.
  * @param firstTradeDate
  *   First date Yahoo has data for, where supplied. A present value signals the instrument existed (used by
  *   [[isLikelyDelisted]]).
  * @param regularMarketTime
  *   Timestamp of the last regular-market print, where supplied.
  * @param regularMarketPrice
  *   Last regular-market price, where supplied.
  * @param chartPreviousClose
  *   Previous close as Yahoo reports it for the chart, where supplied.
  * @param priceHint
  *   Number of decimal places Yahoo recommends for display/rounding, where supplied.
  * @param currentTradingPeriod
  *   The current day's session windows, where supplied. See [[TradingPeriods]].
  * @param dataGranularity
  *   The granularity (resolved interval) echoed by Yahoo for the probe request (e.g. "1d"). Always present.
  * @param range
  *   The range echoed by Yahoo for the probe request (e.g. "1mo"). Always present for the range-based probe.
  * @param validRanges
  *   Ranges Yahoo advertises as valid for this instrument. Unrecognised tokens are dropped.
  * @param hasPrePostMarketData
  *   Whether Yahoo can serve pre/post-market bars for this instrument, where supplied.
  */
final case class HistoryMetadata(
    symbol: String,
    exchangeName: String,
    fullExchangeName: Option[String],
    instrumentType: InstrumentType,
    currency: String,
    exchangeTimezoneName: String,
    timezoneShortName: String,
    gmtOffset: FiniteDuration,
    firstTradeDate: Option[ZonedDateTime],
    regularMarketTime: Option[ZonedDateTime],
    regularMarketPrice: Option[Double],
    chartPreviousClose: Option[Double],
    priceHint: Option[Int],
    currentTradingPeriod: Option[TradingPeriods],
    dataGranularity: String,
    range: String,
    validRanges: List[Range],
    hasPrePostMarketData: Option[Boolean]
) {

  /** The exchange offset as a `java.time.ZoneOffset`, derived from `gmtOffset`. Offset-based (no tz database needed),
    * so it resolves identically on the JVM and Scala.js.
    */
  def exchangeOffset: ZoneOffset = ZoneOffset.ofTotalSeconds(gmtOffset.toSeconds.toInt)

  /** True when `range` is advertised by Yahoo as valid for this instrument. */
  def supports(range: Range): Boolean = validRanges.contains(range)

  /** `regularMarketTime` viewed at the exchange's reported offset (the instant is preserved). Uses the fixed
    * `exchangeOffset`, which is correct for the recent `regularMarketTime` Yahoo returns.
    */
  def regularMarketTimeAtExchange: Option[ZonedDateTime] =
    regularMarketTime.map(_.withZoneSameInstant(exchangeOffset))

  /** Heuristic delisting signal: the instrument has a known first-trade date (so it existed at some point) yet has no
    * regular-market print fresher than `staleThreshold` as of `asOf`. A `true` result is a hint, not a guarantee.
    *
    * @param asOf
    *   The reference instant (typically `Clock[F].realTimeInstant`).
    * @param staleThreshold
    *   How old the last regular-market print may be before the instrument is considered stale.
    */
  def isLikelyDelisted(asOf: Instant, staleThreshold: FiniteDuration): Boolean =
    firstTradeDate.isDefined &&
      regularMarketTime.forall(t => asOf.getEpochSecond - t.toInstant.getEpochSecond > staleThreshold.toSeconds)
}

object HistoryMetadata {

  private def epochToUtc(epochSeconds: Long): ZonedDateTime =
    ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC)

  private def mapTradingPeriod(raw: TradingPeriodRaw): TradingPeriod =
    TradingPeriod(
      start = epochToUtc(raw.start),
      end = epochToUtc(raw.end),
      gmtOffset = raw.gmtoffset.seconds,
      timezoneShortName = raw.timezone
    )

  private def mapTradingPeriods(raw: TradingPeriodsRaw): Either[String, TradingPeriods] =
    raw.regular match {
      case None =>
        Left("currentTradingPeriod is present but missing the 'regular' session")
      case Some(regular) =>
        Right(
          TradingPeriods(
            regular = mapTradingPeriod(regular),
            pre = raw.pre.map(mapTradingPeriod),
            post = raw.post.map(mapTradingPeriod)
          )
        )
    }

  /** Maps Yahoo's raw chart `meta` block to the public model. Returns `Left(message)` when a present trading-period
    * block lacks a regular session; all other absent fields degrade to `None`/empty.
    */
  private[yfinance4s] def fromRaw(raw: ChartMetaRaw): Either[String, HistoryMetadata] =
    (raw.currentTradingPeriod match {
      case None     => Right(None)
      case Some(tp) => mapTradingPeriods(tp).map(Some(_))
    }).map { tradingPeriods =>
      HistoryMetadata(
        symbol = raw.symbol,
        exchangeName = raw.exchangeName,
        fullExchangeName = raw.fullExchangeName,
        instrumentType = InstrumentType.fromString(raw.instrumentType),
        currency = raw.currency,
        exchangeTimezoneName = raw.exchangeTimezoneName,
        timezoneShortName = raw.timezone,
        gmtOffset = raw.gmtoffset.seconds,
        firstTradeDate = raw.firstTradeDate.map(epochToUtc),
        regularMarketTime = raw.regularMarketTime.map(epochToUtc),
        regularMarketPrice = raw.regularMarketPrice,
        chartPreviousClose = raw.chartPreviousClose,
        priceHint = raw.priceHint,
        currentTradingPeriod = tradingPeriods,
        dataGranularity = raw.dataGranularity,
        range = raw.range,
        validRanges = raw.validRanges.getOrElse(Nil).flatMap(Range.withValueOpt),
        hasPrePostMarketData = raw.hasPrePostMarketData
      )
    }
}
