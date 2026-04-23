package org.coinductive.yfinance4s.models

import java.time.{LocalDate, ZonedDateTime}

/** A single earnings event for a specific ticker, returned by
  * [[org.coinductive.yfinance4s.Calendars.getEarningsDates]].
  *
  * Yahoo's per-ticker earnings feed carries event-type metadata (earnings call vs. report vs. stockholders meeting) but
  * omits the market-cap and company-name metadata available in the market-wide feed.
  *
  * @param date
  *   Scheduled or historical date/time of the event. Always present; decode fails if Yahoo omits it.
  * @param eventType
  *   What kind of event this is: call, report, or meeting.
  * @param epsEstimate
  *   Consensus EPS estimate ahead of the event.
  * @param epsActual
  *   Reported EPS (empty for future events).
  * @param surprisePercent
  *   Earnings surprise as a signed percentage.
  * @param timezoneShort
  *   Human-readable timezone abbreviation Yahoo returned (e.g., "EDT"). Not normalized; consult `date`'s
  *   `ZonedDateTime` for the authoritative offset.
  */
final case class EarningsDate(
    date: ZonedDateTime,
    eventType: EarningsEventType,
    epsEstimate: Option[Double],
    epsActual: Option[Double],
    surprisePercent: Option[Double],
    timezoneShort: Option[String]
) {

  /** The event's local date. */
  def localDate: LocalDate = date.toLocalDate

  /** True if this is an earnings call event. */
  def isCall: Boolean = eventType == EarningsEventType.EarningsCall

  /** True if this is an earnings report event. */
  def isReport: Boolean = eventType == EarningsEventType.EarningsReport

  /** True if this is a stockholders meeting event. */
  def isMeeting: Boolean = eventType == EarningsEventType.StockholdersMeeting

  /** True when the event is scheduled strictly after `reference`. */
  def isFuture(reference: ZonedDateTime): Boolean =
    date.isAfter(reference)

  /** True when the event is scheduled strictly before `reference`. */
  def isPast(reference: ZonedDateTime): Boolean =
    date.isBefore(reference)

  /** Beat the consensus: `surprisePercent > 0`. */
  def isBeat: Option[Boolean] = surprisePercent.map(_ > 0)

  /** Missed the consensus: `surprisePercent < 0`. */
  def isMiss: Option[Boolean] = surprisePercent.map(_ < 0)
}

object EarningsDate {

  /** Orders dates ascending (earliest first). */
  implicit val byDateAsc: Ordering[EarningsDate] =
    Ordering.by[EarningsDate, ZonedDateTime](_.date)

  /** Orders dates descending (most recent first) - matches upstream default sort. */
  val byDateDesc: Ordering[EarningsDate] = byDateAsc.reverse
}
