package org.coinductive.yfinance4s

import cats.effect.{Async, Resource, Sync}
import cats.syntax.show._
import io.circe.parser.decode
import org.coinductive.yfinance4s.models.{Interval, Range, Ticker, YFinanceQueryResult}
import retry.{RetryPolicies, RetryPolicy, Sleep}
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.{SttpBackend, UriContext, basicRequest}

import java.time.ZonedDateTime
import scala.concurrent.duration.FiniteDuration

sealed trait YFinanceGateway[F[_]] {
  def getChart(ticker: Ticker, interval: Interval, range: Range): F[YFinanceQueryResult]

  def getChart(
      ticker: Ticker,
      interval: Interval,
      since: ZonedDateTime,
      until: ZonedDateTime
  ): F[YFinanceQueryResult]
}

private object YFinanceGateway {

  def resource[F[_]: Async](
      connectTimeout: FiniteDuration,
      readTimeout: FiniteDuration,
      retries: Int
  ): Resource[F, YFinanceGateway[F]] = {
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

  def apply[F[_]: Sync: Sleep](retries: Int, sttpBackend: SttpBackend[F, Any]): YFinanceGateway[F] = {
    val retryPolicy = RetryPolicies.limitRetries(retries)
    new YFinanceGatewayImpl[F](sttpBackend, retryPolicy)
  }

  private final class YFinanceGatewayImpl[F[_]](
      protected val sttpBackend: SttpBackend[F, Any],
      protected val retryPolicy: RetryPolicy[F]
  )(implicit
      protected val F: Sync[F],
      protected val S: Sleep[F]
  ) extends HTTPBase[F]
      with YFinanceGateway[F] {

    private val ApiEndpoint = uri"https://query1.finance.yahoo.com/v8/finance/chart/"

    def getChart(ticker: Ticker, interval: Interval, range: Range): F[YFinanceQueryResult] = {
      val req =
        basicRequest.get(
          ApiEndpoint.addPath(ticker.show).withParams(("interval", interval.show), ("range", range.show))
        )

      sendRequest(req, parseContent)
    }

    def getChart(
        ticker: Ticker,
        interval: Interval,
        since: ZonedDateTime,
        until: ZonedDateTime
    ): F[YFinanceQueryResult] = {
      val req =
        basicRequest.get(
          ApiEndpoint
            .addPath(ticker.show)
            .withParams(
              ("interval", interval.show),
              ("period1", since.toEpochSecond.show),
              ("period2", until.toEpochSecond.show)
            )
        )

      sendRequest(req, parseContent)
    }

    private def parseContent(content: String): F[YFinanceQueryResult] = {
      decode[YFinanceQueryResult](content)
        .fold(
          e => F.raiseError(new Exception(s"Illegible response: ${e.getMessage}")),
          F.pure
        )
    }

  }

}
