package org.coinductive.yfinance4s

import cats.MonadThrow
import org.coinductive.yfinance4s.models.{Ticker, YFinanceError}
import org.coinductive.yfinance4s.models.internal.YahooErrorBody

/** Maps Yahoo Finance's structured [[YahooErrorBody]] envelope onto the public [[YFinanceError]] hierarchy. Used by the
  * gateway after a `YahooResponse[_]` decodes to a [[org.coinductive.yfinance4s.models.internal.YahooResponse.Failure]]
  *   - the gateway picks [[raiseFor]] for ticker-context endpoints and [[raiseGeneric]] for endpoints without a ticker
  *     (search, screener, market summary, market-wide calendar).
  */
private[yfinance4s] object YahooErrorMapping {

  /** Yahoo's `error.code` sentinel for an unknown or never-existed ticker symbol. */
  val NotFoundCode: String = "Not Found"

  /** Yahoo's `error.code` sentinel for a rate-limited request. Also appears as a substring in some non-structured
    * responses (e.g. the crumb endpoint emits a body containing this phrase under throttling).
    */
  val TooManyRequestsCode: String = "Too Many Requests"

  /** Raises the appropriate `YFinanceError` for an envelope failure on a ticker-context endpoint. `"Not Found"` →
    * `TickerNotFound(ticker)`, `"Too Many Requests"` → `RateLimited(None)`, anything else → `DataParseError`.
    */
  def raiseFor[F[_]: MonadThrow, A](ticker: Ticker, error: YahooErrorBody): F[A] =
    error.code match {
      case NotFoundCode =>
        MonadThrow[F].raiseError(YFinanceError.TickerNotFound(ticker))
      case TooManyRequestsCode =>
        MonadThrow[F].raiseError(YFinanceError.RateLimited(retryAfter = None))
      case _ =>
        MonadThrow[F].raiseError(
          YFinanceError.DataParseError(s"Yahoo query failed: ${error.code} - ${error.description}")
        )
    }

  /** Raises the appropriate `YFinanceError` for an envelope failure on an endpoint without a ticker context. `"Too Many
    * Requests"` → `RateLimited(None)`, anything else (including `"Not Found"`, which is meaningless without a ticker) →
    * `DataParseError` carrying the endpoint label.
    */
  def raiseGeneric[F[_]: MonadThrow, A](label: String, error: YahooErrorBody): F[A] =
    error.code match {
      case TooManyRequestsCode =>
        MonadThrow[F].raiseError(YFinanceError.RateLimited(retryAfter = None))
      case _ =>
        MonadThrow[F].raiseError(
          YFinanceError.DataParseError(s"Yahoo $label query failed: ${error.code} - ${error.description}")
        )
    }
}
