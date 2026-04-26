package org.coinductive.yfinance4s.models

/** Configures how outbound HTTP requests to Yahoo Finance are paced.
  *
  * Yahoo's unofficial API throttles aggressive callers and may return 429 ("Too Many Requests"). Use
  * [[RateLimitConfig.Enabled]] (the default) to apply a request-pacing limit covering all requests issued by a single
  * [[org.coinductive.yfinance4s.YFinanceClient]] instance.
  *
  * Pacing is interval-based: requests are spaced no closer than `1.second / maxRequestsPerSecond` apart, with no burst
  * capacity. A long idle period does not earn credit toward subsequent bursts.
  */
sealed trait RateLimitConfig

object RateLimitConfig {

  /** Disables rate limiting entirely. Useful for tests and for callers who have their own throttle. */
  case object Disabled extends RateLimitConfig

  /** Enables interval-based rate limiting.
    *
    * @param maxRequestsPerSecond
    *   Steady-state rate. Internally converted to a minimum interval of `1.second / maxRequestsPerSecond` between
    *   request starts. Must be positive.
    */
  final case class Enabled(maxRequestsPerSecond: Int) extends RateLimitConfig {
    require(maxRequestsPerSecond > 0, "maxRequestsPerSecond must be positive")
  }

  /** Conservative default: 2 requests per second. */
  val Default: RateLimitConfig = Enabled(maxRequestsPerSecond = 2)
}
