package org.coinductive.yfinance4s

import cats.effect.Sync
import cats.syntax.either.*
import cats.syntax.flatMap.*
import io.circe.Decoder
import io.circe.parser.decode
import org.coinductive.yfinance4s.models.YFinanceError
import retry.{RetryDetails, RetryPolicy, Sleep, retryingOnSomeErrors}
import sttp.client3.{Identity, RequestT, Response, SttpBackend}
import sttp.model.{Header, StatusCode}

import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.util.Try

trait HTTPBase[F[_]] {

  implicit protected val F: Sync[F]
  implicit protected val S: Sleep[F]

  protected val sttpBackend: SttpBackend[F, Any]
  protected val retryPolicy: RetryPolicy[F]
  protected val rateLimiter: RateLimiter[F]

  protected def sendRequest[A](
      request: RequestT[Identity, Either[String, String], Any],
      parseContent: String => F[A]
  ): F[A] =
    retryingOnSomeErrors(
      policy = retryPolicy,
      isWorthRetrying = HTTPBase.isRetriable[F],
      onError = HTTPBase.onErrorSleep[F]
    ) {
      rateLimiter
        .acquire(F.adaptError(request.send(sttpBackend)) { case t => HTTPBase.toNetworkError(t) })
        .flatMap(parseResponse[A](_, parseContent))
    }

  /** Decodes a JSON payload and lifts decode failures into [[YFinanceError.DataParseError]]. `label` is a
    * human-readable name used in the error message (e.g., `"chart"`, `"market summary"`).
    */
  protected def parseAs[A: Decoder](label: String)(content: String): F[A] =
    decode[A](content).fold(
      e => F.raiseError(YFinanceError.DataParseError(s"Failed to parse $label response: ${e.getMessage}", Some(e))),
      F.pure
    )

  private def parseResponse[A](
      response: Response[Either[String, String]],
      parseContent: String => F[A]
  ): F[A] = {
    val status = response.code
    if (response.isSuccess) {
      response.body
        .leftMap(r => YFinanceError.DataParseError(s"Inconsistent response body: $r"): Throwable)
        .fold(F.raiseError, parseContent)
    } else if (status == StatusCode.TooManyRequests) {
      F.raiseError(YFinanceError.RateLimited(HTTPBase.parseRetryAfter(response.headers)))
    } else if (status.isServerError) {
      F.raiseError(
        YFinanceError.NetworkError(s"Yahoo returned HTTP ${status.code}: ${HTTPBase.truncate(response.body.merge)}")
      )
    } else {
      // Yahoo sometimes returns a 4xx envelope with a parseable JSON body carrying its `error` object
      // (e.g., chart endpoint returns 404 + `{"chart":{"error":{...}}}` for unknown tickers).
      // Defer to parseContent so the algebra layer can inspect the structured error and raise the right
      // typed error; if the body isn't parseable, parseAs surfaces the failure as DataParseError.
      parseContent(response.body.merge)
    }
  }
}

private[yfinance4s] object HTTPBase {

  private val ResponseBodyExcerptLimit: Int = 200

  def isRetriable[F[_]: Sync](error: Throwable): F[Boolean] =
    Sync[F].pure(error match {
      case _: YFinanceError.TickerNotFound => false
      case _: YFinanceError.Delisted       => false
      case _: YFinanceError.DataParseError => false
      case _                               => true
    })

  def onErrorSleep[F[_]: Sync: Sleep](error: Throwable, retryDetails: RetryDetails): F[Unit] =
    error match {
      case YFinanceError.RateLimited(Some(retryAfter)) => Sleep[F].sleep(retryAfter)
      case _                                           => Sync[F].unit
    }

  def toNetworkError(t: Throwable): Throwable = t match {
    case ye: YFinanceError => ye
    case other             => YFinanceError.NetworkError(s"HTTP transport failure: ${other.getMessage}", Some(other))
  }

  def parseRetryAfter(headers: Seq[Header]): Option[FiniteDuration] =
    headers
      .find(_.name.equalsIgnoreCase("Retry-After"))
      .map(_.value)
      .flatMap(s => Try(s.trim.toLong).toOption)
      .filter(_ >= 0)
      .map(_.seconds)

  private def truncate(s: String): String =
    if (s.length <= ResponseBodyExcerptLimit) s else s.take(ResponseBodyExcerptLimit)
}
