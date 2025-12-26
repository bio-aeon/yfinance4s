package org.coinductive.yfinance4s

import cats.effect.{Async, Resource}
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend

import scala.concurrent.duration.FiniteDuration

object PlatformSttpBackend extends SttpBackendFactory {
  private val MaxHeaderSize = 32768

  def resource[F[_]: Async](
      connectTimeout: FiniteDuration,
      readTimeout: FiniteDuration
  ): Resource[F, SttpBackend[F, Any]] = {
    val connectTimeoutMs = connectTimeout.toMillis.toInt
    val readTimeoutMs = readTimeout.toMillis.toInt

    AsyncHttpClientCatsBackend
      .resourceUsingConfigBuilder[F](
        _.setConnectTimeout(connectTimeoutMs)
          .setReadTimeout(readTimeoutMs)
          .setRequestTimeout(readTimeoutMs)
          .setHttpClientCodecMaxHeaderSize(MaxHeaderSize)
      )
  }
}
