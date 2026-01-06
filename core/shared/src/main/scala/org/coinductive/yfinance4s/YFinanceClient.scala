package org.coinductive.yfinance4s

import cats.Functor
import cats.effect.{Async, Resource}
import cats.syntax.functor.*
import org.coinductive.yfinance4s.models.{
  ChartResult,
  CorporateActions,
  DividendEvent,
  Interval,
  Range,
  SplitEvent,
  StockResult,
  Ticker,
  YFinanceQueryResult,
  YFinanceQuoteResult
}
import org.coinductive.yfinance4s.models.YFinanceQueryResult.InstrumentData

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

  /** Retrieves dividend history for a ticker.
    *
    * @param ticker
    *   The stock ticker symbol (e.g., Ticker("AAPL"))
    * @param interval
    *   The data interval (typically Interval.`1Day` for dividends)
    * @param range
    *   The time range to query (e.g., Range.`1Year`, Range.Max)
    * @return
    *   An optional list of dividend events, sorted chronologically
    */
  def getDividends(ticker: Ticker, interval: Interval, range: Range): F[Option[List[DividendEvent]]] =
    gateway.getChart(ticker, interval, range).map(extractDividends)

  /** Retrieves dividend history for a ticker within a custom date range.
    *
    * @param ticker
    *   The stock ticker symbol
    * @param interval
    *   The data interval
    * @param since
    *   Start of the date range (inclusive)
    * @param until
    *   End of the date range (inclusive)
    * @return
    *   An optional list of dividend events, sorted chronologically
    */
  def getDividends(
      ticker: Ticker,
      interval: Interval,
      since: ZonedDateTime,
      until: ZonedDateTime
  ): F[Option[List[DividendEvent]]] =
    gateway.getChart(ticker, interval, since, until).map(extractDividends)

  /** Retrieves stock split history for a ticker.
    *
    * @param ticker
    *   The stock ticker symbol
    * @param interval
    *   The data interval (typically Interval.`1Day` for splits)
    * @param range
    *   The time range to query (e.g., Range.Max for all history)
    * @return
    *   An optional list of split events, sorted chronologically
    */
  def getSplits(ticker: Ticker, interval: Interval, range: Range): F[Option[List[SplitEvent]]] =
    gateway.getChart(ticker, interval, range).map(extractSplits)

  /** Retrieves stock split history for a ticker within a custom date range.
    *
    * @param ticker
    *   The stock ticker symbol
    * @param interval
    *   The data interval
    * @param since
    *   Start of the date range (inclusive)
    * @param until
    *   End of the date range (inclusive)
    * @return
    *   An optional list of split events, sorted chronologically
    */
  def getSplits(
      ticker: Ticker,
      interval: Interval,
      since: ZonedDateTime,
      until: ZonedDateTime
  ): F[Option[List[SplitEvent]]] =
    gateway.getChart(ticker, interval, since, until).map(extractSplits)

  /** Retrieves all corporate actions (dividends and splits) for a ticker.
    *
    * @param ticker
    *   The stock ticker symbol
    * @param interval
    *   The data interval
    * @param range
    *   The time range to query
    * @return
    *   An optional CorporateActions object containing both dividends and splits
    */
  def getCorporateActions(ticker: Ticker, interval: Interval, range: Range): F[Option[CorporateActions]] =
    gateway.getChart(ticker, interval, range).map(extractCorporateActions)

  /** Retrieves all corporate actions for a ticker within a custom date range.
    *
    * @param ticker
    *   The stock ticker symbol
    * @param interval
    *   The data interval
    * @param since
    *   Start of the date range (inclusive)
    * @param until
    *   End of the date range (inclusive)
    * @return
    *   An optional CorporateActions object
    */
  def getCorporateActions(
      ticker: Ticker,
      interval: Interval,
      since: ZonedDateTime,
      until: ZonedDateTime
  ): F[Option[CorporateActions]] =
    gateway.getChart(ticker, interval, since, until).map(extractCorporateActions)

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

      val dividends = extractDividendsFromData(data)
      val splits = extractSplitsFromData(data)

      ChartResult(quotes, dividends, splits)
    }
  }

  private def extractDividends(result: YFinanceQueryResult): Option[List[DividendEvent]] =
    result.chart.result.headOption.map(extractDividendsFromData)

  private def extractSplits(result: YFinanceQueryResult): Option[List[SplitEvent]] =
    result.chart.result.headOption.map(extractSplitsFromData)

  private def extractCorporateActions(result: YFinanceQueryResult): Option[CorporateActions] =
    result.chart.result.headOption.map { data =>
      CorporateActions(
        dividends = extractDividendsFromData(data),
        splits = extractSplitsFromData(data)
      )
    }

  private def extractDividendsFromData(data: InstrumentData): List[DividendEvent] =
    data.events
      .flatMap(_.dividends)
      .getOrElse(Map.empty)
      .map { case (timestamp, raw) => DividendEvent.fromRaw(timestamp, raw) }
      .toList
      .sorted

  private def extractSplitsFromData(data: InstrumentData): List[SplitEvent] =
    data.events
      .flatMap(_.splits)
      .getOrElse(Map.empty)
      .map { case (timestamp, raw) => SplitEvent.fromRaw(timestamp, raw) }
      .toList
      .sorted

  private def mapQuoteResult(result: YFinanceQuoteResult) = {
    result.summary.body.quoteSummary.result.headOption.map { quoteData =>
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
        result.fundamentals.body.timeseries.result
          .flatMap(_.trailingPegRatio.headOption.map(_.reportedValue.raw)),
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
