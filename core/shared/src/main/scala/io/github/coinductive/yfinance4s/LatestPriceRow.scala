package io.github.coinductive.yfinance4s

import io.github.coinductive.yfinance4s.PriceRepair.Bar
import io.github.coinductive.yfinance4s.models.Interval
import io.github.coinductive.yfinance4s.models.internal.ChartMetaRaw

import java.time.{LocalDate, ZoneOffset, ZonedDateTime}

/** Settles the row Yahoo adds to a chart for its latest price, which is not a bar of the chart's interval.
  *
  * Yahoo stamps the row at the latest print, except on an intraday chart whose session has closed, where it stamps the
  * row at the session end. On an intraday chart the row is a flat print without volume, inside the last bar's interval
  * or at its end. On a multi-day chart it carries the latest session, which the period bar before it may or may not
  * include yet.
  */
private[yfinance4s] object LatestPriceRow {

  /** The bounds a chart was requested for, when it was requested by dates rather than by range. */
  final case class Window(since: ZonedDateTime, until: ZonedDateTime)

  /** How far a bar reaches past its own timestamp, which decides whether the row falls into it. */
  private sealed trait Span

  private object Span {

    /** An intraday bar takes the row only as a flat print without volume: anywhere inside the bar, or at the bar's end
      * when the row is the closing print of a session that ends with the bar.
      */
    final case class Intraday(end: ZonedDateTime => ZonedDateTime) extends Span

    /** A multi-day bar takes the row anywhere before its period ends, in the exchange's calendar days. */
    final case class MultiDay(end: LocalDate => LocalDate) extends Span
  }

  /** The bars without Yahoo's latest-price row: left out where it lies outside the requested window, and folded into
    * the bar it falls in otherwise. Every other bar passes through unchanged.
    */
  def settle(bars: Vector[Bar], interval: Interval, meta: ChartMetaRaw, window: Option[Window]): Vector[Bar] = {
    val inWindow = window.fold(bars)(w => bars.filterNot(outside(w, meta.regularMarketTime)))
    spanOf(interval).fold(inWindow)(foldLastRow(inWindow, _, meta))
  }

  /** Yahoo appends the row after a past window, and answers a window without a session right after the latest one with
    * the row alone, stamped before the window. Any other bar before `since` stays: Yahoo stamps a New Zealand daily bar
    * at 22:00 UTC on the day before its session.
    */
  private def outside(window: Window, latestPrint: Option[Long])(bar: Bar): Boolean =
    !bar.datetime.isBefore(window.until) ||
      (bar.datetime.isBefore(window.since) && latestPrint.contains(bar.datetime.toEpochSecond))

  /** Daily bars take no row: Yahoo sends a daily chart without one, except for a window without a session. */
  private def spanOf(interval: Interval): Option[Span] = interval match {
    case Interval.`1Minute`   => Some(Span.Intraday(_.plusMinutes(1)))
    case Interval.`2Minutes`  => Some(Span.Intraday(_.plusMinutes(2)))
    case Interval.`5Minutes`  => Some(Span.Intraday(_.plusMinutes(5)))
    case Interval.`15Minutes` => Some(Span.Intraday(_.plusMinutes(15)))
    case Interval.`30Minutes` => Some(Span.Intraday(_.plusMinutes(30)))
    case Interval.`60Minutes` => Some(Span.Intraday(_.plusMinutes(60)))
    case Interval.`90Minutes` => Some(Span.Intraday(_.plusMinutes(90)))
    case Interval.`1Hour`     => Some(Span.Intraday(_.plusHours(1)))
    case Interval.`1Day`      => None
    case Interval.`5Days`     => Some(Span.MultiDay(_.plusDays(5)))
    case Interval.`1Week`     => Some(Span.MultiDay(_.plusWeeks(1)))
    case Interval.`1Month`    => Some(Span.MultiDay(_.plusMonths(1)))
    case Interval.`3Months`   => Some(Span.MultiDay(_.plusMonths(3)))
  }

  private def foldLastRow(bars: Vector[Bar], span: Span, meta: ChartMetaRaw): Vector[Bar] = bars match {
    case init :+ bar :+ row if fallsInto(span, meta)(bar, row) => init :+ folded(bar, row)
    case _                                                     => bars
  }

  private def fallsInto(span: Span, meta: ChartMetaRaw)(bar: Bar, row: Bar): Boolean =
    row.datetime.isAfter(bar.datetime) && (span match {
      case Span.Intraday(end) =>
        val barEnd = end(bar.datetime)
        val insideBar = row.datetime.isBefore(barEnd)
        val closesBar = row.datetime.isEqual(barEnd) && isClosingPrint(row, meta)
        isFlatPrint(row) && (insideBar || closesBar)
      case Span.MultiDay(end) =>
        val exchangeOffset = ZoneOffset.ofTotalSeconds(meta.gmtoffset.toInt)
        row.datetime.withZoneSameInstant(exchangeOffset).toLocalDate.isBefore(end(periodStart(bar, exchangeOffset)))
    })

  /** Whether a flat print at a bar's end is the session's closing print rather than a bar of its own, which is how
    * every zero-volume bar of a currency pair looks. Yahoo stamps the closing print at the latest print, or at the end
    * of the session it reports as current when later prints followed the close.
    */
  private def isClosingPrint(row: Bar, meta: ChartMetaRaw): Boolean = {
    val stamp = row.datetime.toEpochSecond
    meta.regularMarketTime.contains(stamp) || meta.currentTradingPeriod.flatMap(_.regular).exists(_.end == stamp)
  }

  /** The exchange date a multi-day bar's period starts on. Yahoo stamps the bar at local midnight or at the session
    * open, under the offset in force on that day, which a change of daylight saving time can put an hour away from the
    * chart's current offset; so the date is that of the local midnight nearest the stamp.
    */
  private def periodStart(bar: Bar, exchangeOffset: ZoneOffset): LocalDate =
    bar.datetime.withZoneSameInstant(exchangeOffset).plusHours(12).toLocalDate

  private def isFlatPrint(row: Bar): Boolean =
    row.volume == 0L && row.open == row.close && row.high == row.close && row.low == row.close

  /** The bar taking the row: the row's close, the wider high and low, and the row's volume only when the bar's close
    * differs, which shows that Yahoo has not rolled the row's session into the bar yet.
    */
  private def folded(bar: Bar, row: Bar): Bar =
    bar.copy(
      open = if (bar.open.isNaN) row.open else bar.open,
      high = ignoringNaN(math.max)(bar.high, row.high),
      low = ignoringNaN(math.min)(bar.low, row.low),
      close = row.close,
      adjClose = row.adjClose,
      volume = if (bar.close.isNaN || bar.close == row.close) bar.volume else bar.volume + row.volume
    )

  private def ignoringNaN(pick: (Double, Double) => Double)(a: Double, b: Double): Double =
    if (a.isNaN) b else if (b.isNaN) a else pick(a, b)
}
