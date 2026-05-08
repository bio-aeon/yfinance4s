package org.coinductive.yfinance4s.models

import scala.concurrent.duration.FiniteDuration

/** Sealed hierarchy of failures raised by [[org.coinductive.yfinance4s.YFinanceClient]] operations.
  *
  * Every failure surfaced through the `F` channel (`MonadThrow.raiseError`) is one of these five concrete cases or, on
  * pre-existing call sites that have not yet been migrated, a plain `Throwable`. Callers wanting to discriminate should
  * pattern-match on `YFinanceError`; the `case _` arm catches both unmigrated and unrelated failures.
  */
sealed trait YFinanceError extends Throwable

object YFinanceError {

  /** Yahoo Finance throttled the request. The retry-after duration is parsed from the `Retry-After` HTTP response
    * header where present (delta-seconds form), and is `None` otherwise.
    *
    * Raised when:
    *   - HTTP 429 status code, regardless of body
    *   - `YFinanceAuth.fetchCrumb` body contains "Too Many Requests"
    *   - `Calendars` API returns `error.code = "Too Many Requests"`
    *
    * Retried automatically by the library's internal retry policy, with the optional retry-after honored as a sleep
    * between attempts. Callers seeing this error after retries are exhausted have already exceeded the configured retry
    * budget.
    */
  final case class RateLimited(retryAfter: Option[FiniteDuration])
      extends RuntimeException(
        s"Yahoo Finance rate-limited the request" +
          retryAfter.fold("")(d => s" (Retry-After: ${d.toSeconds}s)")
      )
      with YFinanceError

  /** The ticker symbol does not exist on Yahoo Finance.
    *
    * Raised when:
    *   - The chart endpoint returns `chart.error.code = "Not Found"` for the given ticker
    *   - `getOptions`, `getHolders`, `getAnalystData` return an `error` field referencing the ticker
    *   - A multi-ticker download method (`downloadCharts`, `downloadStocks`, `Tickers.history`, ...) finds no data for
    *     the ticker
    *
    * Not retried - retry cannot fix a missing symbol.
    */
  final case class TickerNotFound(ticker: Ticker)
      extends RuntimeException(s"Ticker not found on Yahoo Finance: ${ticker.value}")
      with YFinanceError

  /** The ticker may have been delisted - it appears to have been valid once but Yahoo no longer returns price data.
    *
    * Reserved for future phases that have stronger delisting signals than Yahoo's current single error shape (which
    * uses the same `"Not Found"` code for both never-existed and delisted symbols). Phase 10.3 ships the type but does
    * not raise it from gateway paths; Phase 10.5 (History Metadata) and Phase 11 (Price Repair) will start producing
    * it.
    *
    * Not retried.
    */
  final case class Delisted(ticker: Ticker)
      extends RuntimeException(s"Ticker may be delisted on Yahoo Finance: ${ticker.value}")
      with YFinanceError

  /** Yahoo's response could not be interpreted as the expected shape.
    *
    * Raised when:
    *   - The response body fails JSON decoding (Circe error)
    *   - A required field is absent or has an unparseable value (e.g., calendar row missing `startdatetime`)
    *   - HTTP 4xx other than 429 (the response shape is not a successful payload)
    *   - `YFinanceAuth.fetchCrumb` returns an empty body or unrecognised content
    *
    * The `cause` carries the underlying decode/parse exception for diagnostics where one exists, or `None` for
    * synthesised errors (e.g. unexpected HTTP status with no upstream throwable). Not retried - the same broken request
    * will produce the same broken response.
    */
  final case class DataParseError(message: String, cause: Option[Throwable] = None)
      extends RuntimeException(message)
      with YFinanceError

  /** The HTTP request failed at the transport or server layer.
    *
    * Raised when:
    *   - sttp's transport throws (DNS failure, connection refused, read timeout, TLS error)
    *   - HTTP 5xx server status (Yahoo briefly unhealthy)
    *
    * The `cause` carries the underlying transport exception where one exists, or `None` for synthesised server-error
    * cases (e.g. an HTTP 5xx response with no upstream throwable). Retried automatically by the library's internal
    * retry policy.
    */
  final case class NetworkError(message: String, cause: Option[Throwable] = None)
      extends RuntimeException(message)
      with YFinanceError
}
