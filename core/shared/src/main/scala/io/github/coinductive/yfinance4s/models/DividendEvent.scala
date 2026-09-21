package io.github.coinductive.yfinance4s.models

import io.github.coinductive.yfinance4s.models.internal.DividendEventRaw

import java.time.{Instant, ZoneOffset, ZonedDateTime}

/** Represents a dividend payment event for a stock.
  *
  * @param exDate
  *   The ex-dividend date. Shareholders who own the stock before this date are entitled to receive the dividend.
  * @param amount
  *   The dividend amount per share, in `currency` where that is present and in the stock's trading currency otherwise.
  * @param currency
  *   The dividend's currency where Yahoo reports one (rare - seen for issuers paying in a different currency than the
  *   share price). Absent means the amount is in the instrument's trading currency. On a repaired chart's dividends
  *   this reflects any standardisation or FX conversion applied (see [[PriceRepairConfig]]).
  */
final case class DividendEvent(
    exDate: ZonedDateTime,
    amount: Double,
    currency: Option[String] = None
) {

  /** Returns the dividend yield relative to a given share price.
    *
    * @param sharePrice
    *   The share price to calculate yield against
    * @return
    *   The dividend yield as a decimal (e.g., 0.005 for 0.5%)
    */
  def yieldAt(sharePrice: Double): Double =
    if (sharePrice > 0) amount / sharePrice else 0.0
}

object DividendEvent {

  /** Creates a DividendEvent from the raw API response data.
    *
    * @param timestampKey
    *   The string key from the dividends map (Unix timestamp)
    * @param raw
    *   The raw dividend event from Yahoo Finance API
    * @return
    *   A DividendEvent with proper ZonedDateTime conversion
    */
  private[yfinance4s] def fromRaw(timestampKey: String, raw: DividendEventRaw): DividendEvent = {
    val epochSeconds = timestampKey.toLong
    DividendEvent(
      exDate = ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC),
      amount = raw.amount,
      currency = raw.normalisedCurrency
    )
  }

  /** Ordering for DividendEvent by ex-date (chronological).
    */
  implicit val ordering: Ordering[DividendEvent] = Ordering.by(_.exDate.toInstant)
}
