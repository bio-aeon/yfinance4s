package org.coinductive.yfinance4s

import cats.MonadThrow
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.show._
import io.circe.parser.decode
import sttp.client3.{Response, SttpBackend, UriContext, basicRequest}

trait Gateway[F[_]] {
  def getChart(ticker: Ticker, interval: Interval, range: Range): F[YFinanceQueryResult]
}

object Gateway {

  def apply[F[_]: MonadThrow](sttpBackend: SttpBackend[F, Any]): Gateway[F] = new GatewayImpl[F](sttpBackend)

  private final class GatewayImpl[F[_]](sttpBackend: SttpBackend[F, Any])(implicit F: MonadThrow[F])
      extends Gateway[F] {

    private val apiEndpoint = uri"https://query1.finance.yahoo.com/v8/finance/chart/"

    def getChart(ticker: Ticker, interval: Interval, range: Range): F[YFinanceQueryResult] = {
      val req =
        basicRequest.get(
          apiEndpoint.addPath(ticker.show).withParams(("interval", interval.show), ("range", range.show))
        )
      req.send(sttpBackend).flatMap(parseResponse)
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
