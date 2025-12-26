package org.coinductive.yfinance4s

import cats.effect.{Async, Resource}
import sttp.client3.SttpBackend
import sttp.client3.impl.cats.FetchCatsBackend

import scala.concurrent.duration.FiniteDuration

object PlatformSttpBackend extends SttpBackendFactory {
  def resource[F[_]: Async](
      connectTimeout: FiniteDuration,
      readTimeout: FiniteDuration
  ): Resource[F, SttpBackend[F, Any]] =
    Resource.pure(FetchCatsBackend[F]())
}
