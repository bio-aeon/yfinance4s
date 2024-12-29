package org.coinductive.yfinance4s

import cats.effect.{Async, Resource, Sync}
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.show._
import io.circe.parser.decode
import retry.{RetryPolicies, RetryPolicy, Sleep, retryingOnAllErrors}
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.{Response, SttpBackend, UriContext, basicRequest}

import scala.concurrent.duration.FiniteDuration

trait Gateway[F[_]] {
  def getChart(ticker: Ticker, interval: Interval, range: Range): F[YFinanceQueryResult]
}

object Gateway {

  def resource[F[_]: Async](
      connectTimeout: FiniteDuration,
      readTimeout: FiniteDuration,
      retries: Int
  ): Resource[F, Gateway[F]] = {
    val connectTimeoutMs = connectTimeout.toMillis.toInt
    val readTimeoutMs = readTimeout.toMillis.toInt

    AsyncHttpClientCatsBackend
      .resourceUsingConfigBuilder[F](
        _.setConnectTimeout(connectTimeoutMs)
          .setReadTimeout(readTimeoutMs)
          .setRequestTimeout(readTimeoutMs)
      )
      .map(apply[F](retries, _))
  }

  def apply[F[_]: Sync: Sleep](retries: Int, sttpBackend: SttpBackend[F, Any]): Gateway[F] = {
    val retryPolicy = RetryPolicies.limitRetries(retries)
    new GatewayImpl[F](sttpBackend, retryPolicy)
  }

  private final class GatewayImpl[F[_]: Sleep](sttpBackend: SttpBackend[F, Any], retryPolicy: RetryPolicy[F])(implicit
      F: Sync[F]
  ) extends Gateway[F] {

    private val apiEndpoint = uri"https://query1.finance.yahoo.com/v8/finance/chart/"

    def getChart(ticker: Ticker, interval: Interval, range: Range): F[YFinanceQueryResult] = {
      val req =
        basicRequest.get(
          apiEndpoint.addPath(ticker.show).withParams(("interval", interval.show), ("range", range.show))
        )

      retryingOnAllErrors(policy = retryPolicy, (_: Throwable, _) => F.unit) {
        req.send(sttpBackend)
      }.flatMap(parseResponse)
    }

    private def parseResponse(
        response: Response[Either[String, String]]
    ): F[YFinanceQueryResult] = {
      if (response.isSuccess) {
        response.body
          .leftMap(r => new Exception(s"Inconsistent response: $r"))
          .fold(
            F.raiseError,
            decode[YFinanceQueryResult](_)
              .fold(
                e => F.raiseError(new Exception(s"Illegible response: ${e.getMessage}")),
                F.pure
              )
          )
      } else {
        F.raiseError(new Exception(s"Unexpected code: ${response.code}. Details: ${response.body.merge}"))
      }
    }

  }

}
