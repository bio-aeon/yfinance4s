package org.coinductive.yfinance4s

import cats.MonadThrow
import cats.syntax.all.*
import io.circe.Json
import io.circe.syntax.*
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.internal.*

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
import scala.util.Try

/** Algebra for Yahoo Finance calendar-style queries.
  *
  * Phase 6.1 exposes earnings queries; subsequent phases will add IPO (6.2), economic-event (6.3), and splits (6.4)
  * calendar methods.
  */
trait Calendars[F[_]] {

  /** Upcoming and recent earnings events across all tickers in a date range, ordered by market capitalization
    * descending by default (server-side via `config.sort`).
    *
    * @param start
    *   Inclusive lower bound for the earnings event date. Defaults to `LocalDate.now()` in the system default zone;
    *   pass `LocalDate.now(ZoneOffset.UTC)` for zone-independent queries.
    * @param end
    *   Inclusive upper bound for the earnings event date. Defaults to one week after `start`.
    * @param config
    *   Pagination, limit, sort, and market-cap threshold. See [[CalendarConfig]].
    */
  def getEarningsCalendar(
      start: LocalDate = LocalDate.now(),
      end: LocalDate = LocalDate.now().plusDays(Calendars.DefaultEarningsCalendarDays),
      config: CalendarConfig = CalendarConfig.Default
  ): F[List[EarningsEvent]]

  /** Historical and upcoming earnings events for a specific ticker, ordered by date descending (most recent first).
    *
    * @param ticker
    *   Ticker to query. Expected in Yahoo's canonical uppercase form (e.g., `"AAPL"`); Yahoo rejects lowercase.
    * @param limit
    *   Number of events to return. Yahoo caps at 100; defaults to 12.
    * @param offset
    *   Skip the first `offset` events.
    */
  def getEarningsDates(
      ticker: Ticker,
      limit: Int = Calendars.DefaultEarningsDatesLimit,
      offset: Int = 0
  ): F[List[EarningsDate]]
}

private[yfinance4s] object Calendars {

  val DefaultEarningsCalendarDays: Int = 7
  val DefaultEarningsDatesLimit: Int = 12
  val MaxLimit: Int = 100

  private val DateFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

  private val SpEarningsEntityType: String = "sp_earnings"
  private val EarningsEntityType: String = "earnings"

  private val MarketWideContext: String = "earnings calendar"
  private val PerTickerContext: String = "earnings dates"

  private val UsRegion: String = "us"

  private object Field {
    val Ticker: String = "ticker"
    val CompanyShortName: String = "companyshortname"
    val IntradayMarketCap: String = "intradaymarketcap"
    val EventName: String = "eventname"
    val StartDateTime: String = "startdatetime"
    val StartDateTimeType: String = "startdatetimetype"
    val EpsEstimate: String = "epsestimate"
    val EpsActual: String = "epsactual"
    val EpsSurprisePct: String = "epssurprisepct"
    val EventType: String = "eventtype"
    val Region: String = "region"
    val TimeZoneShortName: String = "timeZoneShortName"
  }

  private object Operator {
    val Eq: String = "EQ"
    val Gte: String = "GTE"
    val Lte: String = "LTE"
    val And: String = "AND"
    val Or: String = "OR"
  }

  private object EventTypeCode {
    val EarningsAnnouncementDate: String = "EAD"
    val EarningsReleaseAnnouncement: String = "ERA"
  }

  private val MarketWideFields: List[String] = List(
    Field.Ticker,
    Field.CompanyShortName,
    Field.IntradayMarketCap,
    Field.EventName,
    Field.StartDateTime,
    Field.StartDateTimeType,
    Field.EpsEstimate,
    Field.EpsActual,
    Field.EpsSurprisePct
  )

  private val PerTickerFields: List[String] = List(
    Field.StartDateTime,
    Field.TimeZoneShortName,
    Field.EpsEstimate,
    Field.EpsActual,
    Field.EpsSurprisePct,
    Field.EventType
  )

  def apply[F[_]: MonadThrow](gateway: YFinanceGateway[F], auth: YFinanceAuth[F]): Calendars[F] =
    new CalendarsImpl(gateway, auth)

  // --- Pure helpers (no F) exposed for unit testing ----------------------

  private[yfinance4s] def buildMarketWideBody(start: LocalDate, end: LocalDate, config: CalendarConfig): String = {
    val eventTypeOr: Json = operand(
      Operator.Or,
      List(
        eqOperand(Field.EventType, EventTypeCode.EarningsAnnouncementDate),
        eqOperand(Field.EventType, EventTypeCode.EarningsReleaseAnnouncement)
      )
    )
    val baseOperands: List[Json] = List(
      eqOperand(Field.Region, UsRegion),
      eventTypeOr,
      operand(Operator.Gte, List(Json.fromString(Field.StartDateTime), Json.fromString(start.format(DateFmt)))),
      operand(Operator.Lte, List(Json.fromString(Field.StartDateTime), Json.fromString(end.format(DateFmt))))
    )
    val operands: List[Json] = config.marketCap.fold(baseOperands) { mc =>
      baseOperands :+ operand(Operator.Gte, List(Json.fromString(Field.IntradayMarketCap), numericJson(mc)))
    }
    val query = operand(Operator.And, operands)
    buildBody(SpEarningsEntityType, MarketWideFields, config.limit, config.offset, config.sort, query)
  }

  private[yfinance4s] def buildPerTickerBody(ticker: Ticker, limit: Int, offset: Int): String = {
    val query = eqOperand(Field.Ticker, ticker.value)
    buildBody(EarningsEntityType, PerTickerFields, limit, offset, CalendarSort.ByDateDesc, query)
  }

  private def buildBody(
      entityIdType: String,
      includeFields: List[String],
      size: Int,
      offset: Int,
      sort: CalendarSort,
      query: Json
  ): String = {
    val json = Json.obj(
      "sortType" -> Json.fromString(sort.order.apiValue),
      "entityIdType" -> Json.fromString(entityIdType),
      "sortField" -> Json.fromString(sort.field),
      "includeFields" -> includeFields.asJson,
      "size" -> Json.fromInt(math.min(size, MaxLimit)),
      "offset" -> Json.fromInt(offset),
      "query" -> query
    )
    json.noSpaces
  }

  private def operand(op: String, args: List[Json]): Json =
    Json.obj("operator" -> Json.fromString(op), "operands" -> Json.arr(args *))

  private def eqOperand(field: String, value: String): Json =
    operand(Operator.Eq, List(Json.fromString(field), Json.fromString(value)))

  private def numericJson(value: Double): Json =
    if (value == math.floor(value) && !value.isInfinite) Json.fromLong(value.toLong)
    else Json.fromDoubleOrNull(value)

  /** Accepts Yahoo's loose ISO-8601 (with or without offset) and assumes UTC when the offset is missing. Yahoo's
    * documented format is `YYYY-MM-DDTHH:mm:ss` without a trailing 'Z'.
    */
  private def parseZonedDateTime(s: String): Option[ZonedDateTime] =
    Try(ZonedDateTime.parse(s)).toOption
      .orElse(Try(java.time.LocalDateTime.parse(s).atZone(ZoneOffset.UTC)).toOption)

  // --- F-polymorphic impl ------------------------------------------------

  private final class CalendarsImpl[F[_]](
      gateway: YFinanceGateway[F],
      auth: YFinanceAuth[F]
  )(implicit F: MonadThrow[F])
      extends Calendars[F] {

    def getEarningsCalendar(
        start: LocalDate,
        end: LocalDate,
        config: CalendarConfig
    ): F[List[EarningsEvent]] =
      auth.getCredentials.flatMap { creds =>
        val body = buildMarketWideBody(start, end, config)
        gateway.postVisualization(body, creds).flatMap(raiseOnError).flatMap(mapMarketWide)
      }

    def getEarningsDates(ticker: Ticker, limit: Int, offset: Int): F[List[EarningsDate]] =
      auth.getCredentials.flatMap { creds =>
        val body = buildPerTickerBody(ticker, limit, offset)
        gateway.postVisualization(body, creds).flatMap(raiseOnError).flatMap(mapPerTicker)
      }

    // --- Error escalation -------------------------------------------------

    private def raiseOnError(r: YFinanceCalendarResult): F[YFinanceCalendarResult] =
      r.error.fold(F.pure(r))(err => F.raiseError(err.toException))

    // --- Row mapping ------------------------------------------------------
    //
    // Structural fields (API-guaranteed) raise loudly when absent or malformed.
    // Soft fields (best-effort) fall through to Option when the value is null or the column is missing.

    private def mapMarketWide(raw: YFinanceCalendarResult): F[List[EarningsEvent]] =
      raw.rows.traverse(mapMarketWideRow(raw.columnIndex)).map(_.sorted(EarningsEvent.byDateAsc))

    private def mapMarketWideRow(idx: Map[String, Int])(row: CalendarRow): F[EarningsEvent] =
      for {
        symbolStr <- requireString(row, idx, Field.Ticker, MarketWideContext)
        startStr <- requireString(row, idx, Field.StartDateTime, MarketWideContext)
        start <- parseZdt(startStr, Field.StartDateTime, MarketWideContext)
        timingRaw <- requireString(row, idx, Field.StartDateTimeType, MarketWideContext)
        timing <- requireEnum(
          EarningsTiming.withValueOpt(timingRaw),
          timingRaw,
          Field.StartDateTimeType,
          MarketWideContext
        )
      } yield EarningsEvent(
        symbol = Ticker(symbolStr),
        companyName = optString(row, idx, Field.CompanyShortName),
        marketCap = optLong(row, idx, Field.IntradayMarketCap),
        eventName = optString(row, idx, Field.EventName),
        startDateTime = start,
        timing = timing,
        epsEstimate = optDouble(row, idx, Field.EpsEstimate),
        epsActual = optDouble(row, idx, Field.EpsActual),
        surprisePercent = optDouble(row, idx, Field.EpsSurprisePct)
      )

    private def mapPerTicker(raw: YFinanceCalendarResult): F[List[EarningsDate]] =
      raw.rows.traverse(mapPerTickerRow(raw.columnIndex)).map(_.sorted(EarningsDate.byDateDesc))

    private def mapPerTickerRow(idx: Map[String, Int])(row: CalendarRow): F[EarningsDate] =
      for {
        startStr <- requireString(row, idx, Field.StartDateTime, PerTickerContext)
        start <- parseZdt(startStr, Field.StartDateTime, PerTickerContext)
        eventTypeRaw <- requireString(row, idx, Field.EventType, PerTickerContext)
        eventType <- requireEnum(
          EarningsEventType.withValueOpt(eventTypeRaw),
          eventTypeRaw,
          Field.EventType,
          PerTickerContext
        )
      } yield EarningsDate(
        date = start,
        eventType = eventType,
        epsEstimate = optDouble(row, idx, Field.EpsEstimate),
        epsActual = optDouble(row, idx, Field.EpsActual),
        surprisePercent = optDouble(row, idx, Field.EpsSurprisePct),
        timezoneShort = optString(row, idx, Field.TimeZoneShortName)
      )

    // --- Field accessors --------------------------------------------------

    private def requireString(
        row: CalendarRow,
        idx: Map[String, Int],
        field: String,
        context: String
    ): F[String] =
      idx.get(field).flatMap(row.stringAt) match {
        case Some(v) => F.pure(v)
        case None    => F.raiseError(new Exception(s"Missing required field '$field' in $context response row"))
      }

    private def parseZdt(s: String, field: String, context: String): F[ZonedDateTime] =
      parseZonedDateTime(s) match {
        case Some(v) => F.pure(v)
        case None    => F.raiseError(new Exception(s"Unparseable '$field' in $context response row: '$s'"))
      }

    private def requireEnum[A](lookup: Option[A], raw: String, field: String, context: String): F[A] =
      lookup match {
        case Some(v) => F.pure(v)
        case None    => F.raiseError(new Exception(s"Unknown '$field' value in $context response row: '$raw'"))
      }

    private def optString(row: CalendarRow, idx: Map[String, Int], field: String): Option[String] =
      idx.get(field).flatMap(row.stringAt)

    private def optLong(row: CalendarRow, idx: Map[String, Int], field: String): Option[Long] =
      idx.get(field).flatMap(row.longAt)

    private def optDouble(row: CalendarRow, idx: Map[String, Int], field: String): Option[Double] =
      idx.get(field).flatMap(row.doubleAt)
  }
}
