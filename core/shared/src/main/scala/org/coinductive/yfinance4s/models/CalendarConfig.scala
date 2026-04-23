package org.coinductive.yfinance4s.models

/** Configuration for a custom earnings calendar query. Shape mirrors [[ScreenerConfig]] for familiarity.
  *
  * @param limit
  *   Maximum events to return (1..100, Yahoo-capped).
  * @param offset
  *   Skip the first N events (for pagination).
  * @param marketCap
  *   If set, filter to events whose company has at least this USD market cap.
  * @param sort
  *   Server-side sort field/order. Default is market cap descending.
  */
final case class CalendarConfig(
    limit: Int = CalendarConfig.DefaultLimit,
    offset: Int = 0,
    marketCap: Option[Double] = None,
    sort: CalendarSort = CalendarSort.Default
)

object CalendarConfig {
  val DefaultLimit: Int = 25
  val MaxLimit: Int = 100

  val Default: CalendarConfig = CalendarConfig()
}

/** Server-side sort rule for calendar queries. Reuses [[SortOrder]] from the screener module. */
final case class CalendarSort(field: String, order: SortOrder)

object CalendarSort {
  val Default: CalendarSort = CalendarSort("intradaymarketcap", SortOrder.Desc)
  val ByDateAsc: CalendarSort = CalendarSort("startdatetime", SortOrder.Asc)
  val ByDateDesc: CalendarSort = CalendarSort("startdatetime", SortOrder.Desc)
}
