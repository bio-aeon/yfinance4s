package org.coinductive.yfinance4s.models

import org.coinductive.yfinance4s.models.YFinanceQueryResult.SplitEventRaw

import java.time.{Instant, ZoneOffset, ZonedDateTime}

/** Represents a stock split event.
  *
  * A stock split changes the number of shares outstanding while maintaining the same total market capitalization. In a
  * 4:1 split, each shareholder receives 4 new shares for every 1 share they held.
  *
  * @param exDate
  *   The effective date of the stock split.
  * @param numerator
  *   Number of new shares received.
  * @param denominator
  *   Number of old shares exchanged.
  * @param splitRatio
  *   Human-readable ratio string (e.g., "4:1" for a 4-for-1 split).
  */
final case class SplitEvent(
    exDate: ZonedDateTime,
    numerator: Int,
    denominator: Int,
    splitRatio: String
) {

  /** The split factor as a decimal ratio. For a 4:1 split, this returns 4.0. For a 1:10 reverse split, this returns
    * 0.1.
    */
  def factor: Double = numerator.toDouble / denominator.toDouble

  /** Whether this is a forward split (increasing share count). A forward split has numerator > denominator.
    */
  def isForwardSplit: Boolean = numerator > denominator

  /** Whether this is a reverse split (decreasing share count). A reverse split has numerator < denominator.
    */
  def isReverseSplit: Boolean = numerator < denominator
}

object SplitEvent {

  /** Creates a SplitEvent from the raw API response data.
    *
    * @param timestampKey
    *   The string key from the splits map (Unix timestamp)
    * @param raw
    *   The raw split event from Yahoo Finance API
    * @return
    *   A SplitEvent with proper ZonedDateTime conversion
    */
  private[yfinance4s] def fromRaw(timestampKey: String, raw: SplitEventRaw): SplitEvent = {
    val epochSeconds = timestampKey.toLong
    SplitEvent(
      exDate = ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC),
      numerator = raw.numerator,
      denominator = raw.denominator,
      splitRatio = raw.splitRatio
    )
  }

  /** Ordering for SplitEvent by ex-date (chronological).
    */
  implicit val ordering: Ordering[SplitEvent] = Ordering.by(_.exDate.toInstant)
}
