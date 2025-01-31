package org.coinductive.yfinance4s

import cats.effect.Sync
import cats.syntax.either._
import cats.syntax.flatMap._
import retry.{RetryPolicy, Sleep, retryingOnAllErrors}
import sttp.client3.{Identity, RequestT, Response, SttpBackend}

trait HTTPBase[F[_]] {

  implicit protected val F: Sync[F]
  implicit protected val S: Sleep[F]

  protected val sttpBackend: SttpBackend[F, Any]
  protected val retryPolicy: RetryPolicy[F]

  protected def sendRequest[A](
      request: RequestT[Identity, Either[String, String], Any],
      parseContent: String => F[A]
  ): F[A] = {
    retryingOnAllErrors(policy = retryPolicy, (_: Throwable, _) => F.unit) {
      request.send(sttpBackend)
    }.flatMap(parseResponse[A](_, parseContent))
  }

  private def parseResponse[A](
      response: Response[Either[String, String]],
      parseContent: String => F[A]
  ): F[A] = {
    if (response.isSuccess) {
      response.body
        .leftMap(r => new Exception(s"Inconsistent response: $r"))
        .fold(F.raiseError, parseContent)
    } else {
      F.raiseError(new Exception(s"Unexpected code: ${response.code}. Details: ${response.body.merge}"))
    }
  }

}
