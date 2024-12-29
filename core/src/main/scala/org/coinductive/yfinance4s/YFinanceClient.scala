package org.coinductive.yfinance4s

import cats.Functor
import cats.effect.{Async, Resource}
import cats.syntax.functor._
import org.coinductive.yfinance4s.models.{ChartResult, Interval, Range, Ticker, YFinanceQueryResult}

import java.time.{Instant, ZoneOffset, ZonedDateTime}

final class YFinanceClient[F[_]: Functor] private (gateway: YFinanceGateway[F]) {

  def getChart(ticker: Ticker, interval: Interval, range: Range): F[Option[ChartResult]] =
    gateway.getChart(ticker, interval, range).map(mapResult)

  def getChart(
      ticker: Ticker,
      interval: Interval,
      since: ZonedDateTime,
      until: ZonedDateTime
  ): F[Option[ChartResult]] = gateway.getChart(ticker, interval, since, until).map(mapResult)

  private def mapResult(result: YFinanceQueryResult): Option[ChartResult] = {
    result.chart.result.headOption.map { data =>
      val quotes = (0 until data.timestamp.length).map { i =>
        val quote = data.indicators.quote.head
        val adjclose = data.indicators.adjclose.head
        ChartResult.Quote(
          ZonedDateTime.ofInstant(Instant.ofEpochSecond(data.timestamp(i)), ZoneOffset.UTC),
          quote.close(i),
          quote.open(i),
          quote.volume(i),
          quote.high(i),
          quote.low(i),
          adjclose.adjclose(i)
        )
      }.toList

      ChartResult(quotes)
    }
  }

}

object YFinanceClient {

  def resource[F[_]: Async](config: YFinanceClientConfig): Resource[F, YFinanceClient[F]] = {
    YFinanceGateway.resource[F](config.connectTimeout, config.readTimeout, config.retries).map(new YFinanceClient(_))
  }

}
