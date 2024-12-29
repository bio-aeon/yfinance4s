package org.coinductive.yfinance4s

import cats.effect.{Async, Resource}
import org.coinductive.yfinance4s.models.{Interval, Range, Ticker, YFinanceQueryResult}

import java.time.ZonedDateTime

final class YFinanceClient[F[_]] private (gateway: YFinanceGateway[F]) {

  def getChart(ticker: Ticker, interval: Interval, range: Range): F[YFinanceQueryResult] =
    gateway.getChart(ticker, interval, range)

  def getChart(
      ticker: Ticker,
      interval: Interval,
      since: ZonedDateTime,
      until: ZonedDateTime
  ): F[YFinanceQueryResult] = gateway.getChart(ticker, interval, since, until)

}

object YFinanceClient {

  def resource[F[_]: Async](config: YFinanceClientConfig): Resource[F, YFinanceClient[F]] = {
    YFinanceGateway.resource[F](config.connectTimeout, config.readTimeout, config.retries).map(new YFinanceClient(_))
  }

}
