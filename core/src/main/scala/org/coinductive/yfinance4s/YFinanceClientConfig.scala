package org.coinductive.yfinance4s

import scala.concurrent.duration.FiniteDuration

final case class YFinanceClientConfig(connectTimeout: FiniteDuration, readTimeout: FiniteDuration, retries: Int)
