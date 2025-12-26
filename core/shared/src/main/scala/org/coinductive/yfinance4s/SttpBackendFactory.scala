package org.coinductive.yfinance4s

import cats.effect.{Async, Resource}
import sttp.client3.SttpBackend

import scala.concurrent.duration.FiniteDuration

trait SttpBackendFactory {
  def resource[F[_]: Async](
      connectTimeout: FiniteDuration,
      readTimeout: FiniteDuration
  ): Resource[F, SttpBackend[F, Any]]
}
