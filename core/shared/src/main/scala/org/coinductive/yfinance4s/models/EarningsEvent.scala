package org.coinductive.yfinance4s.models

import java.time.{LocalDate, ZonedDateTime}

/** A scheduled earnings event from Yahoo Finance's market-wide calendar feed.
  *
  * Returned by [[org.coinductive.yfinance4s.Calendars.getEarningsCalendar]]. Unlike [[EarningsDate]], this carries
  * company and market-cap metadata plus a scheduling-timing modifier. Unlike [[EarningsHistory]] (which comes from the
  * analyst-trend API and uses quarter labels), this has an explicit `ZonedDateTime` and covers future events.
  *
  * @param symbol
  *   Ticker symbol of the reporting company.
  * @param companyName
  *   Human-readable short name (e.g., "Apple Inc"), if Yahoo supplies it.
  * @param marketCap
  *   Intraday market cap in USD, if available.
  * @param eventName
  *   Yahoo's display name for the event (e.g., "Apple Inc Q4 2025 Earnings Call").
  * @param startDateTime
  *   Scheduled date and time of the event. Always present; decode fails if Yahoo omits it.
  * @param timing
  *   When during the trading day the release is scheduled.
  * @param epsEstimate
  *   Consensus EPS estimate ahead of the release.
  * @param epsActual
  *   Reported EPS (empty for future events).
  * @param surprisePercent
  *   Earnings surprise as a signed percentage.
  */
final case class EarningsEvent(
    symbol: Ticker,
    companyName: Option[String],
    marketCap: Option[Long],
    eventName: Option[String],
    startDateTime: ZonedDateTime,
    timing: EarningsTiming,
    epsEstimate: Option[Double],
    epsActual: Option[Double],
    surprisePercent: Option[Double]
) {

  /** Local date portion of the scheduled event. */
  def date: LocalDate = startDateTime.toLocalDate

  /** True when the event is scheduled strictly after `reference`. */
  def isFuture(reference: ZonedDateTime): Boolean =
    startDateTime.isAfter(reference)

  /** True when the event is scheduled strictly before `reference`. */
  def isPast(reference: ZonedDateTime): Boolean =
    startDateTime.isBefore(reference)

  /** Beat the consensus: `surprisePercent > 0`. */
  def isBeat: Option[Boolean] = surprisePercent.map(_ > 0)

  /** Missed the consensus: `surprisePercent < 0`. */
  def isMiss: Option[Boolean] = surprisePercent.map(_ < 0)

  /** Release scheduled before the US regular session opens. */
  def isBeforeMarketOpen: Boolean =
    timing == EarningsTiming.BeforeMarketOpen

  /** Release scheduled after the US regular session closes. */
  def isAfterMarketClose: Boolean =
    timing == EarningsTiming.AfterMarketClose
}

object EarningsEvent {

  /** Orders events by scheduled date/time ascending (earliest first). */
  implicit val byDateAsc: Ordering[EarningsEvent] =
    Ordering.by[EarningsEvent, ZonedDateTime](_.startDateTime)

  /** Orders events by scheduled date/time descending (latest first). */
  val byDateDesc: Ordering[EarningsEvent] = byDateAsc.reverse

  /** Orders events by market cap descending (largest first); entries with no market cap sort last. */
  val byMarketCapDesc: Ordering[EarningsEvent] =
    Ordering.by[EarningsEvent, Long](_.marketCap.getOrElse(Long.MinValue)).reverse
}
