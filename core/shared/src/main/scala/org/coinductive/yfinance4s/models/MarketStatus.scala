package org.coinductive.yfinance4s.models

import java.time.ZonedDateTime
import scala.concurrent.duration.{Duration, FiniteDuration, MILLISECONDS}

/** Timezone descriptor associated with a [[MarketStatus]]. `short` and `gmtOffsetMillis` are structurally guaranteed by
  * Yahoo's `/markettime` response; `full` is a human-readable label that Yahoo sometimes omits.
  */
final case class MarketTimezone(
    short: String,
    gmtOffsetMillis: Long,
    full: Option[String] = None
) {

  /** Offset expressed as a `FiniteDuration`. Use `.toSeconds` / `.toHours` for unit conversions. */
  def gmtOffset: FiniteDuration = Duration(gmtOffsetMillis, MILLISECONDS)
}

/** Trading status for a [[MarketRegion]]: open/closed, session hours, timezone.
  *
  * Fields structurally guaranteed by Yahoo's `/markettime` endpoint (`status`, `open`, `close`, `timezone`) are
  * required. Auxiliary metadata (`message`, `yfitMarketState`) is optional.
  */
final case class MarketStatus(
    region: MarketRegion,
    status: String,
    open: ZonedDateTime,
    close: ZonedDateTime,
    timezone: MarketTimezone,
    message: Option[String] = None,
    yfitMarketState: Option[String] = None
) {

  /** True when Yahoo reports the market as `"open"` (case-insensitive). */
  def isOpen: Boolean = status.equalsIgnoreCase(MarketStatus.OpenState)

  /** True when Yahoo reports a non-regular session state (closed, pre/post-market). */
  def isClosed: Boolean = MarketStatus.ClosedStates.contains(status.toLowerCase)

  /** Duration of the regular session. `open` and `close` are required, so this is always defined. */
  def sessionDuration: FiniteDuration =
    Duration(java.time.Duration.between(open, close).toMillis, MILLISECONDS)
}

object MarketStatus {

  private val OpenState: String = "open"
  private val ClosedStates: Set[String] = Set("closed", "pre-market", "post-market", "pre", "post")
}
