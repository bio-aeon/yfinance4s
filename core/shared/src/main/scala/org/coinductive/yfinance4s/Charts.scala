package org.coinductive.yfinance4s

import cats.Functor
import cats.syntax.functor.*
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.internal.{Chart, InstrumentData, YFinanceQuoteResult}

import java.time.{Instant, ZoneOffset, ZonedDateTime}

/** Algebra for historical chart data, stock quotes, and corporate actions. */
trait Charts[F[_]] {

  /** Retrieves historical chart data (OHLCV) for a ticker by range. */
  def getChart(ticker: Ticker, interval: Interval, range: Range): F[Option[ChartResult]]

  /** Retrieves historical chart data (OHLCV) for a ticker by date range. */
  def getChart(
      ticker: Ticker,
      interval: Interval,
      since: ZonedDateTime,
      until: ZonedDateTime
  ): F[Option[ChartResult]]

  /** Retrieves current quote with fundamentals for a ticker. */
  def getStock(ticker: Ticker): F[Option[StockResult]]

  /** Retrieves dividend history for a ticker. */
  def getDividends(ticker: Ticker, interval: Interval, range: Range): F[Option[List[DividendEvent]]]

  /** Retrieves dividend history for a ticker within a custom date range. */
  def getDividends(
      ticker: Ticker,
      interval: Interval,
      since: ZonedDateTime,
      until: ZonedDateTime
  ): F[Option[List[DividendEvent]]]

  /** Retrieves stock split history for a ticker. */
  def getSplits(ticker: Ticker, interval: Interval, range: Range): F[Option[List[SplitEvent]]]

  /** Retrieves stock split history for a ticker within a custom date range. */
  def getSplits(
      ticker: Ticker,
      interval: Interval,
      since: ZonedDateTime,
      until: ZonedDateTime
  ): F[Option[List[SplitEvent]]]

  /** Retrieves all corporate actions (dividends and splits) for a ticker. */
  def getCorporateActions(ticker: Ticker, interval: Interval, range: Range): F[Option[CorporateActions]]

  /** Retrieves all corporate actions for a ticker within a custom date range. */
  def getCorporateActions(
      ticker: Ticker,
      interval: Interval,
      since: ZonedDateTime,
      until: ZonedDateTime
  ): F[Option[CorporateActions]]
}

private[yfinance4s] object Charts {

  def apply[F[_]: Functor](gateway: YFinanceGateway[F], scrapper: YFinanceScrapper[F]): Charts[F] =
    new ChartsImpl(gateway, scrapper)

  private final class ChartsImpl[F[_]: Functor](
      gateway: YFinanceGateway[F],
      scrapper: YFinanceScrapper[F]
  ) extends Charts[F] {

    def getChart(ticker: Ticker, interval: Interval, range: Range): F[Option[ChartResult]] =
      gateway.getChart(ticker, interval, range).map(mapChart)

    def getChart(
        ticker: Ticker,
        interval: Interval,
        since: ZonedDateTime,
        until: ZonedDateTime
    ): F[Option[ChartResult]] = gateway.getChart(ticker, interval, since, until).map(mapChart)

    def getStock(ticker: Ticker): F[Option[StockResult]] =
      scrapper.getQuote(ticker).map(_.flatMap(mapQuoteResult))

    def getDividends(ticker: Ticker, interval: Interval, range: Range): F[Option[List[DividendEvent]]] =
      gateway.getChart(ticker, interval, range).map(extractDividends)

    def getDividends(
        ticker: Ticker,
        interval: Interval,
        since: ZonedDateTime,
        until: ZonedDateTime
    ): F[Option[List[DividendEvent]]] =
      gateway.getChart(ticker, interval, since, until).map(extractDividends)

    def getSplits(ticker: Ticker, interval: Interval, range: Range): F[Option[List[SplitEvent]]] =
      gateway.getChart(ticker, interval, range).map(extractSplits)

    def getSplits(
        ticker: Ticker,
        interval: Interval,
        since: ZonedDateTime,
        until: ZonedDateTime
    ): F[Option[List[SplitEvent]]] =
      gateway.getChart(ticker, interval, since, until).map(extractSplits)

    def getCorporateActions(ticker: Ticker, interval: Interval, range: Range): F[Option[CorporateActions]] =
      gateway.getChart(ticker, interval, range).map(extractCorporateActions)

    def getCorporateActions(
        ticker: Ticker,
        interval: Interval,
        since: ZonedDateTime,
        until: ZonedDateTime
    ): F[Option[CorporateActions]] =
      gateway.getChart(ticker, interval, since, until).map(extractCorporateActions)

    // --- Private Mapping Helpers ---

    private def mapChart(chart: Chart): Option[ChartResult] =
      chart.result.headOption.map { data =>
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

    private def extractDividends(chart: Chart): Option[List[DividendEvent]] =
      chart.result.headOption.map(extractDividendsFromData)

    private def extractSplits(chart: Chart): Option[List[SplitEvent]] =
      chart.result.headOption.map(extractSplitsFromData)

    private def extractCorporateActions(chart: Chart): Option[CorporateActions] =
      chart.result.headOption.map { data =>
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

    private def mapQuoteResult(result: YFinanceQuoteResult): Option[StockResult] =
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
