package org.coinductive.yfinance4s

import cats.Applicative
import cats.effect.{Async, Resource}
import org.coinductive.yfinance4s.models.RateLimitConfig
import upperbound.Limiter

import scala.concurrent.duration.DurationInt

/** Paces outbound HTTP requests according to a [[RateLimitConfig]].
  *
  * A thin internal facade around `upperbound.Limiter` so that the rest of the codebase stays decoupled from the
  * concrete implementation library.
  */
private[yfinance4s] sealed trait RateLimiter[F[_]] {

  /** Wraps `fa` so that it does not start until the limiter issues a permit. */
  def acquire[A](fa: F[A]): F[A]
}

private[yfinance4s] object RateLimiter {

  /** Allocates a `RateLimiter` matching the supplied [[RateLimitConfig]]. Returns a no-op for `Disabled` and a
    * permit-issuing limiter for `Enabled`. The returned `Resource` releases any underlying scheduling resources on
    * close.
    */
  def resource[F[_]: Async](config: RateLimitConfig): Resource[F, RateLimiter[F]] =
    config match {
      case RateLimitConfig.Disabled =>
        Resource.pure[F, RateLimiter[F]](noop[F])
      case RateLimitConfig.Enabled(maxRequestsPerSecond) =>
        val minInterval = 1.second / maxRequestsPerSecond.toLong
        Limiter.start[F](minInterval).map(fromUpperbound[F])
    }

  /** A `RateLimiter` that never paces. Used for [[RateLimitConfig.Disabled]] and tests. */
  def noop[F[_]: Applicative]: RateLimiter[F] = new RateLimiter[F] {
    def acquire[A](fa: F[A]): F[A] = fa
  }

  private def fromUpperbound[F[_]](limiter: Limiter[F]): RateLimiter[F] =
    new RateLimiter[F] {
      def acquire[A](fa: F[A]): F[A] = limiter.submit(fa)
    }
}
