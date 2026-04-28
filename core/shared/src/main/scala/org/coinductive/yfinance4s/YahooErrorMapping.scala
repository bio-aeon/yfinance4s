package org.coinductive.yfinance4s

import cats.MonadThrow
import org.coinductive.yfinance4s.models.{Ticker, YFinanceError}
import org.coinductive.yfinance4s.models.internal.YahooErrorBody

/** Maps Yahoo Finance's structured [[YahooErrorBody]] envelope onto the public [[YFinanceError]] hierarchy. Shared
  * across every algebra that decodes a response containing an `error: Option[YahooErrorBody]` field (chart, options,
  * holders, analysts, calendar, …) so each endpoint applies the same code-to-error policy.
  */
private[yfinance4s] object YahooErrorMapping {

  /** Yahoo's `error.code` sentinel for an unknown or never-existed ticker symbol. */
  val NotFoundCode: String = "Not Found"

  /** Yahoo's `error.code` sentinel for a rate-limited request. Also appears as a substring in some non-structured
    * responses (e.g. the crumb endpoint emits a body containing this phrase under throttling).
    */
  val TooManyRequestsCode: String = "Too Many Requests"

  /** Raises the appropriate `YFinanceError` when the response carries an error envelope; passes through unchanged
    * otherwise. Use via `flatTap` to keep the surrounding result value:
    *
    * {{{
    * gateway.getOptions(ticker, creds).flatTap { r =>
    *   YahooErrorMapping.raiseIfPresent[F](ticker, r.optionChain.error)
    * }
    * }}}
    */
  def raiseIfPresent[F[_]: MonadThrow](ticker: Ticker, error: Option[YahooErrorBody]): F[Unit] =
    error match {
      case Some(YahooErrorBody(NotFoundCode, _)) =>
        MonadThrow[F].raiseError(YFinanceError.TickerNotFound(ticker))
      case Some(err) =>
        MonadThrow[F].raiseError(
          YFinanceError.DataParseError(s"Yahoo query failed: ${err.code} - ${err.description}")
        )
      case None =>
        MonadThrow[F].unit
    }
}
