package org.coinductive.yfinance4s

import cats.Functor
import cats.effect.{Async, Resource}
import cats.syntax.functor._
import org.coinductive.yfinance4s.models.{
  ChartResult,
  Interval,
  Range,
  StockResult,
  Ticker,
  YFinanceQueryResult,
  YFinanceQuoteResult
}

import java.time.{Instant, ZoneOffset, ZonedDateTime}

final class YFinanceClient[F[_]: Functor] private (gateway: YFinanceGateway[F], scrapper: YFinanceScrapper[F]) {

  def getChart(ticker: Ticker, interval: Interval, range: Range): F[Option[ChartResult]] =
    gateway.getChart(ticker, interval, range).map(mapQueryResult)

  def getChart(
      ticker: Ticker,
      interval: Interval,
      since: ZonedDateTime,
      until: ZonedDateTime
  ): F[Option[ChartResult]] = gateway.getChart(ticker, interval, since, until).map(mapQueryResult)

  def getStock(ticker: Ticker): F[Option[StockResult]] = {
    scrapper.getQuote(ticker).map(_.flatMap(mapQuoteResult))
  }

  private def mapQueryResult(result: YFinanceQueryResult): Option[ChartResult] = {
    result.chart.result.headOption.map { data =>
      val quotes = data.timestamp.indices.map { i =>
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

  private def mapQuoteResult(result: YFinanceQuoteResult) = {
    result.body.quoteSummary.result.headOption.map { quoteData =>
      val price = quoteData.price
      val profile = quoteData.summaryProfile
      val details = quoteData.summaryDetail
      val financials = quoteData.financialData
      val stats = quoteData.defaultKeyStatistics
      StockResult(
        price.symbol,
        price.longName,
        price.quoteType,
        price.currency,
        price.regularMarketPrice.raw,
        price.regularMarketChangePercent.raw,
        price.marketCap.raw,
        price.exchangeName,
        profile.sector,
        profile.industry,
        profile.longBusinessSummary,
        details.trailingPE.map(_.raw),
        details.forwardPE.map(_.raw),
        details.dividendYield.map(_.raw),
        financials.totalCash.raw,
        financials.totalDebt.raw,
        financials.totalRevenue.raw,
        financials.ebitda.raw,
        financials.debtToEquity.raw,
        financials.revenuePerShare.raw,
        financials.returnOnAssets.raw,
        financials.returnOnEquity.raw,
        financials.freeCashflow.raw,
        financials.operatingCashflow.raw,
        financials.earningsGrowth.raw,
        financials.revenueGrowth.raw,
        financials.grossMargins.raw,
        financials.ebitdaMargins.raw,
        financials.operatingMargins.raw,
        financials.profitMargins.raw,
        stats.enterpriseValue.raw,
        stats.floatShares.raw,
        stats.sharesOutstanding.raw,
        stats.sharesShort.raw,
        stats.shortRatio.raw,
        stats.shortPercentOfFloat.raw,
        stats.impliedSharesOutstanding.raw,
        stats.netIncomeToCommon.raw,
        stats.pegRatio.raw,
        stats.enterpriseToRevenue.raw,
        stats.enterpriseToEbitda.raw,
        stats.bookValue.map(_.raw),
        stats.priceToBook.map(_.raw),
        stats.trailingEps.map(_.raw),
        stats.forwardEps.map(_.raw)
      )
    }
  }

}

object YFinanceClient {

  def resource[F[_]: Async](config: YFinanceClientConfig): Resource[F, YFinanceClient[F]] = {
    for {
      gateway <- YFinanceGateway.resource[F](config.connectTimeout, config.readTimeout, config.retries)
      scrapper <- YFinanceScrapper.resource[F](config.connectTimeout, config.readTimeout, config.retries)
    } yield new YFinanceClient(gateway, scrapper)
  }

}
