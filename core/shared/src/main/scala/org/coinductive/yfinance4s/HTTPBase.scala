package org.coinductive.yfinance4s

import cats.effect.Sync
import cats.syntax.either.*
import cats.syntax.flatMap.*
import io.circe.Decoder
import io.circe.parser.decode
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

  /** Decodes a JSON payload and lifts decode failures into the effect. `label` is a human-readable name used in the
    * error message (e.g., `"chart"`, `"market summary"`).
    */
  protected def parseAs[A: Decoder](label: String)(content: String): F[A] =
    decode[A](content).fold(
      e => F.raiseError(new Exception(s"Failed to parse $label response: ${e.getMessage}")),
      F.pure
    )

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
