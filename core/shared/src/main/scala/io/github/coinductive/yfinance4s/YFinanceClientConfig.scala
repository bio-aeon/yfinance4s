package io.github.coinductive.yfinance4s

import io.github.coinductive.yfinance4s.models.RateLimitConfig

import scala.concurrent.duration.FiniteDuration

final case class YFinanceClientConfig(
    connectTimeout: FiniteDuration,
    readTimeout: FiniteDuration,
    retries: Int,
    rateLimit: RateLimitConfig = RateLimitConfig.Default
)
