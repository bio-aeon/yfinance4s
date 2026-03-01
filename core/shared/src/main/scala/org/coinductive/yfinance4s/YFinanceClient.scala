package org.coinductive.yfinance4s

import cats.Monad
import cats.data.NonEmptyList
import cats.effect.{Async, Concurrent, Resource}
import cats.effect.syntax.concurrent.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import io.scalaland.chimney.dsl.*
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.YFinanceQueryResult.InstrumentData

import java.time.{Instant, LocalDate, ZoneOffset, ZonedDateTime}

/** Effectful Yahoo Finance client for fetching financial data.
  *
  * Provides methods for retrieving historical price data, current quotes, corporate actions, options chains, holders
  * information, financial statements, and analyst data. All operations are wrapped in an effect type `F[_]`.
  *
  * Obtain an instance via [[YFinanceClient.resource]].
  */
trait YFinanceClient[F[_]] {

  // --- Chart / Historical Data ---

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

  // --- Corporate Actions ---

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

  // --- Options ---

  /** Retrieves all available option expiration dates for a ticker. */
  def getOptionExpirations(ticker: Ticker): F[Option[List[LocalDate]]]

  /** Retrieves the option chain for a specific expiration date. */
  def getOptionChain(ticker: Ticker, expirationDate: LocalDate): F[Option[OptionChain]]

  /** Retrieves the nearest expiration's option chain along with all available expirations. */
  def getFullOptionChain(ticker: Ticker): F[Option[FullOptionChain]]

  // --- Holders ---

  /** Retrieves major holders breakdown for a ticker. */
  def getMajorHolders(ticker: Ticker): F[Option[MajorHolders]]

  /** Retrieves top institutional holders for a ticker. */
  def getInstitutionalHolders(ticker: Ticker): F[List[InstitutionalHolder]]

  /** Retrieves top mutual fund holders for a ticker. */
  def getMutualFundHolders(ticker: Ticker): F[List[MutualFundHolder]]

  /** Retrieves recent insider transactions for a ticker. */
  def getInsiderTransactions(ticker: Ticker): F[List[InsiderTransaction]]

  /** Retrieves insider roster (current positions) for a ticker. */
  def getInsiderRoster(ticker: Ticker): F[List[InsiderRosterEntry]]

  /** Retrieves comprehensive holders data for a ticker. */
  def getHoldersData(ticker: Ticker): F[Option[HoldersData]]

  // --- Financial Statements ---

  /** Retrieves comprehensive financial statements for a ticker. */
  def getFinancialStatements(
      ticker: Ticker,
      frequency: Frequency = Frequency.Yearly
  ): F[Option[FinancialStatements]]

  /** Retrieves income statements for a ticker. */
  def getIncomeStatements(
      ticker: Ticker,
      frequency: Frequency = Frequency.Yearly
  ): F[List[IncomeStatement]]

  /** Retrieves balance sheets for a ticker. */
  def getBalanceSheets(
      ticker: Ticker,
      frequency: Frequency = Frequency.Yearly
  ): F[List[BalanceSheet]]

  /** Retrieves cash flow statements for a ticker. */
  def getCashFlowStatements(
      ticker: Ticker,
      frequency: Frequency = Frequency.Yearly
  ): F[List[CashFlowStatement]]

  // --- Analyst Data ---

  /** Retrieves analyst consensus price targets for a ticker. */
  def getAnalystPriceTargets(ticker: Ticker): F[Option[AnalystPriceTargets]]

  /** Retrieves analyst recommendation trends for a ticker. */
  def getRecommendations(ticker: Ticker): F[List[RecommendationTrend]]

  /** Retrieves analyst upgrade/downgrade history for a ticker. */
  def getUpgradeDowngradeHistory(ticker: Ticker): F[List[UpgradeDowngrade]]

  /** Retrieves analyst earnings (EPS) estimates for a ticker. */
  def getEarningsEstimates(ticker: Ticker): F[List[EarningsEstimate]]

  /** Retrieves analyst revenue estimates for a ticker. */
  def getRevenueEstimates(ticker: Ticker): F[List[RevenueEstimate]]

  /** Retrieves historical earnings (actual vs. estimate) for a ticker. */
  def getEarningsHistory(ticker: Ticker): F[List[EarningsHistory]]

  /** Retrieves growth estimates with index comparison for a ticker. */
  def getGrowthEstimates(ticker: Ticker): F[List[GrowthEstimates]]

  /** Retrieves comprehensive analyst data for a ticker. */
  def getAnalystData(ticker: Ticker): F[Option[AnalystData]]

  // --- Multi-Ticker Downloads (concrete with default implementations) ---

  /** Downloads chart data for multiple tickers in parallel. Fails if any ticker fetch fails. */
  def downloadCharts(
      tickers: NonEmptyList[Ticker],
      interval: Interval,
      range: Range,
      parallelism: Int = YFinanceClient.DefaultParallelism
  )(implicit C: Concurrent[F]): F[Map[Ticker, ChartResult]] =
    YFinanceClient.downloadMulti(tickers, parallelism) { ticker =>
      getChart(ticker, interval, range).flatMap {
        case Some(result) => C.pure(result)
        case None         => C.raiseError[ChartResult](new NoSuchElementException(s"No chart data for ${ticker.value}"))
      }
    }

  /** Downloads chart data for multiple tickers in parallel within a date range. Fails if any ticker fetch fails. */
  def downloadCharts(
      tickers: NonEmptyList[Ticker],
      interval: Interval,
      since: ZonedDateTime,
      until: ZonedDateTime,
      parallelism: Int
  )(implicit C: Concurrent[F]): F[Map[Ticker, ChartResult]] =
    YFinanceClient.downloadMulti(tickers, parallelism) { ticker =>
      getChart(ticker, interval, since, until).flatMap {
        case Some(result) => C.pure(result)
        case None         => C.raiseError[ChartResult](new NoSuchElementException(s"No chart data for ${ticker.value}"))
      }
    }

  /** Downloads current stock quotes for multiple tickers in parallel. Fails if any ticker fetch fails. */
  def downloadStocks(
      tickers: NonEmptyList[Ticker],
      parallelism: Int = YFinanceClient.DefaultParallelism
  )(implicit C: Concurrent[F]): F[Map[Ticker, StockResult]] =
    YFinanceClient.downloadMulti(tickers, parallelism) { ticker =>
      getStock(ticker).flatMap {
        case Some(result) => C.pure(result)
        case None         => C.raiseError[StockResult](new NoSuchElementException(s"No stock data for ${ticker.value}"))
      }
    }

  /** Downloads financial statements for multiple tickers in parallel. Fails if any ticker fetch fails. */
  def downloadFinancialStatements(
      tickers: NonEmptyList[Ticker],
      frequency: Frequency = Frequency.Yearly,
      parallelism: Int = YFinanceClient.DefaultParallelism
  )(implicit C: Concurrent[F]): F[Map[Ticker, FinancialStatements]] =
    YFinanceClient.downloadMulti(tickers, parallelism) { ticker =>
      getFinancialStatements(ticker, frequency).flatMap {
        case Some(result) => C.pure(result)
        case None =>
          C.raiseError[FinancialStatements](new NoSuchElementException(s"No financial data for ${ticker.value}"))
      }
    }
}

object YFinanceClient {

  private[yfinance4s] val DefaultParallelism = 4

  def resource[F[_]: Async](config: YFinanceClientConfig): Resource[F, YFinanceClient[F]] = {
    for {
      gateway <- YFinanceGateway.resource[F](config.connectTimeout, config.readTimeout, config.retries)
      scrapper <- YFinanceScrapper.resource[F](config.connectTimeout, config.readTimeout, config.retries)
      auth <- YFinanceAuth.resource[F](config.connectTimeout, config.readTimeout, config.retries)
    } yield new Impl(gateway, scrapper, auth)
  }

  private def downloadMulti[F[_], A](
      tickers: NonEmptyList[Ticker],
      parallelism: Int
  )(fetch: Ticker => F[A])(implicit C: Concurrent[F]): F[Map[Ticker, A]] =
    tickers.toList
      .parTraverseN(parallelism) { ticker =>
        fetch(ticker).map(ticker -> _)
      }
      .map(_.toMap)

  private final class Impl[F[_]: Monad](
      gateway: YFinanceGateway[F],
      scrapper: YFinanceScrapper[F],
      auth: YFinanceAuth[F]
  ) extends YFinanceClient[F] {

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

    def getOptionExpirations(ticker: Ticker): F[Option[List[LocalDate]]] =
      auth.getCredentials.flatMap { credentials =>
        gateway.getOptions(ticker, credentials).map(extractExpirations)
      }

    def getOptionChain(ticker: Ticker, expirationDate: LocalDate): F[Option[OptionChain]] = {
      val epochSeconds = expirationDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond
      auth.getCredentials.flatMap { credentials =>
        gateway.getOptions(ticker, epochSeconds, credentials).map(extractOptionChain(_, expirationDate))
      }
    }

    def getFullOptionChain(ticker: Ticker): F[Option[FullOptionChain]] =
      auth.getCredentials.flatMap { credentials =>
        gateway.getOptions(ticker, credentials).map(mapToFullOptionChain)
      }

    def getMajorHolders(ticker: Ticker): F[Option[MajorHolders]] =
      auth.getCredentials.flatMap { credentials =>
        gateway.getHolders(ticker, credentials).map(extractMajorHolders)
      }

    def getInstitutionalHolders(ticker: Ticker): F[List[InstitutionalHolder]] =
      auth.getCredentials.flatMap { credentials =>
        gateway.getHolders(ticker, credentials).map(extractInstitutionalHolders)
      }

    def getMutualFundHolders(ticker: Ticker): F[List[MutualFundHolder]] =
      auth.getCredentials.flatMap { credentials =>
        gateway.getHolders(ticker, credentials).map(extractMutualFundHolders)
      }

    def getInsiderTransactions(ticker: Ticker): F[List[InsiderTransaction]] =
      auth.getCredentials.flatMap { credentials =>
        gateway.getHolders(ticker, credentials).map(extractInsiderTransactions)
      }

    def getInsiderRoster(ticker: Ticker): F[List[InsiderRosterEntry]] =
      auth.getCredentials.flatMap { credentials =>
        gateway.getHolders(ticker, credentials).map(extractInsiderRoster)
      }

    def getHoldersData(ticker: Ticker): F[Option[HoldersData]] =
      auth.getCredentials.flatMap { credentials =>
        gateway.getHolders(ticker, credentials).map(mapToHoldersData)
      }

    // --- Financial Statements ---

    def getFinancialStatements(
        ticker: Ticker,
        frequency: Frequency
    ): F[Option[FinancialStatements]] =
      gateway.getFinancials(ticker, frequency).map(mapFinancialStatements(ticker, _))

    def getIncomeStatements(
        ticker: Ticker,
        frequency: Frequency
    ): F[List[IncomeStatement]] =
      gateway.getFinancials(ticker, frequency, "income").map(extractIncomeStatements)

    def getBalanceSheets(
        ticker: Ticker,
        frequency: Frequency
    ): F[List[BalanceSheet]] =
      gateway.getFinancials(ticker, frequency, "balance-sheet").map(extractBalanceSheets)

    def getCashFlowStatements(
        ticker: Ticker,
        frequency: Frequency
    ): F[List[CashFlowStatement]] =
      gateway.getFinancials(ticker, frequency, "cash-flow").map(extractCashFlowStatements)

    // --- Analyst Data ---

    private def fetchAnalystData[A](ticker: Ticker)(extract: YFinanceAnalystResult => A): F[A] =
      auth.getCredentials.flatMap { credentials =>
        gateway.getAnalystData(ticker, credentials).map(extract)
      }

    def getAnalystPriceTargets(ticker: Ticker): F[Option[AnalystPriceTargets]] =
      fetchAnalystData(ticker)(extractPriceTargets)

    def getRecommendations(ticker: Ticker): F[List[RecommendationTrend]] =
      fetchAnalystData(ticker)(extractRecommendations)

    def getUpgradeDowngradeHistory(ticker: Ticker): F[List[UpgradeDowngrade]] =
      fetchAnalystData(ticker)(extractUpgradeDowngrades)

    def getEarningsEstimates(ticker: Ticker): F[List[EarningsEstimate]] =
      fetchAnalystData(ticker)(extractEarningsEstimates)

    def getRevenueEstimates(ticker: Ticker): F[List[RevenueEstimate]] =
      fetchAnalystData(ticker)(extractRevenueEstimates)

    def getEarningsHistory(ticker: Ticker): F[List[EarningsHistory]] =
      fetchAnalystData(ticker)(extractEarningsHistoryEntries)

    def getGrowthEstimates(ticker: Ticker): F[List[GrowthEstimates]] =
      fetchAnalystData(ticker)(extractGrowthEstimates)

    def getAnalystData(ticker: Ticker): F[Option[AnalystData]] =
      fetchAnalystData(ticker)(mapToAnalystData)

    // --- Private Mapping Helpers ---

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

    private def extractExpirations(result: YFinanceOptionsResult): Option[List[LocalDate]] =
      result.optionChain.result.headOption.map { data =>
        data.expirationDates.map(epochToLocalDate).sorted
      }

    private def extractOptionChain(result: YFinanceOptionsResult, requestedDate: LocalDate): Option[OptionChain] =
      result.optionChain.result.headOption.flatMap { data =>
        data.options
          .find(container => epochToLocalDate(container.expirationDate) == requestedDate)
          .map(container => buildOptionChain(container, data.strikes))
      }

    private def mapToFullOptionChain(result: YFinanceOptionsResult): Option[FullOptionChain] =
      result.optionChain.result.headOption.map { data =>
        val expirations = data.expirationDates.map(epochToLocalDate).sorted
        val underlyingPrice = data.quote.flatMap(_.regularMarketPrice)

        val chains = data.options.map { container =>
          val date = epochToLocalDate(container.expirationDate)
          date -> buildOptionChain(container, data.strikes)
        }.toMap

        FullOptionChain(
          underlyingSymbol = data.underlyingSymbol,
          underlyingPrice = underlyingPrice,
          expirationDates = expirations,
          chains = chains
        )
      }

    private def buildOptionChain(container: OptionsContainerRaw, allStrikes: List[Double]): OptionChain = {
      val expirationDate = epochToLocalDate(container.expirationDate)
      val calls = container.calls.map(rawToContract(_, OptionType.Call, expirationDate)).sorted
      val puts = container.puts.map(rawToContract(_, OptionType.Put, expirationDate)).sorted
      val activeStrikes = (calls.map(_.strike) ++ puts.map(_.strike)).distinct.sorted

      OptionChain(
        expirationDate = expirationDate,
        calls = calls,
        puts = puts,
        strikes = activeStrikes,
        hasMiniOptions = container.hasMiniOptions
      )
    }

    private def rawToContract(
        raw: OptionContractRaw,
        optionType: OptionType,
        expiration: LocalDate
    ): OptionContract =
      OptionContract(
        contractSymbol = raw.contractSymbol,
        optionType = optionType,
        strike = raw.strike,
        expiration = expiration,
        currency = raw.currency,
        lastPrice = raw.lastPrice,
        change = raw.change,
        percentChange = raw.percentChange,
        bid = raw.bid,
        ask = raw.ask,
        volume = raw.volume,
        openInterest = raw.openInterest,
        impliedVolatility = raw.impliedVolatility.map(_ * 100.0),
        inTheMoney = raw.inTheMoney,
        lastTradeDate = raw.lastTradeDate.map(epochToZonedDateTime),
        contractSize = raw.contractSize.map(ContractSize.fromString).getOrElse(ContractSize.Regular)
      )

    private def epochToLocalDate(epochSeconds: Long): LocalDate =
      Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDate

    private def epochToZonedDateTime(epochSeconds: Long): ZonedDateTime =
      ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC)

    // --- Holders Data Mapping ---

    private def extractMajorHolders(result: YFinanceHoldersResult): Option[MajorHolders] =
      result.quoteSummary.result.headOption.flatMap { data =>
        data.majorHoldersBreakdown.flatMap(mapMajorHolders)
      }

    private def extractInstitutionalHolders(result: YFinanceHoldersResult): List[InstitutionalHolder] =
      result.quoteSummary.result.headOption
        .flatMap(_.institutionOwnership)
        .flatMap(_.ownershipList)
        .getOrElse(List.empty)
        .flatMap(mapInstitutionalHolder)
        .sorted

    private def extractMutualFundHolders(result: YFinanceHoldersResult): List[MutualFundHolder] =
      result.quoteSummary.result.headOption
        .flatMap(_.fundOwnership)
        .flatMap(_.ownershipList)
        .getOrElse(List.empty)
        .flatMap(mapMutualFundHolder)
        .sorted

    private def extractInsiderTransactions(result: YFinanceHoldersResult): List[InsiderTransaction] =
      result.quoteSummary.result.headOption
        .flatMap(_.insiderTransactions)
        .flatMap(_.transactions)
        .getOrElse(List.empty)
        .flatMap(mapInsiderTransaction)
        .sorted

    private def extractInsiderRoster(result: YFinanceHoldersResult): List[InsiderRosterEntry] =
      result.quoteSummary.result.headOption
        .flatMap(_.insiderHolders)
        .flatMap(_.holders)
        .getOrElse(List.empty)
        .flatMap(mapInsiderRosterEntry)
        .sorted

    private def mapToHoldersData(result: YFinanceHoldersResult): Option[HoldersData] =
      result.quoteSummary.result.headOption.map { data =>
        HoldersData(
          majorHolders = data.majorHoldersBreakdown.flatMap(mapMajorHolders),
          institutionalHolders = data.institutionOwnership
            .flatMap(_.ownershipList)
            .getOrElse(List.empty)
            .flatMap(mapInstitutionalHolder)
            .sorted,
          mutualFundHolders = data.fundOwnership
            .flatMap(_.ownershipList)
            .getOrElse(List.empty)
            .flatMap(mapMutualFundHolder)
            .sorted,
          insiderTransactions = data.insiderTransactions
            .flatMap(_.transactions)
            .getOrElse(List.empty)
            .flatMap(mapInsiderTransaction)
            .sorted,
          insiderRoster = data.insiderHolders
            .flatMap(_.holders)
            .getOrElse(List.empty)
            .flatMap(mapInsiderRosterEntry)
            .sorted
        )
      }

    private def mapMajorHolders(raw: MajorHoldersBreakdownRaw): Option[MajorHolders] =
      for {
        insiders <- raw.insidersPercentHeld.map(_.raw)
        institutions <- raw.institutionsPercentHeld.map(_.raw)
        institutionsFloat <- raw.institutionsFloatPercentHeld.map(_.raw)
        institutionsCount <- raw.institutionsCount.map(_.raw)
      } yield MajorHolders(insiders, institutions, institutionsFloat, institutionsCount)

    private def mapInstitutionalHolder(raw: OwnershipEntryRaw): Option[InstitutionalHolder] =
      for {
        org <- raw.organization
        reportDate <- raw.reportDate.map(v => epochToLocalDate(v.raw))
        pctHeld <- raw.pctHeld.map(_.raw)
        position <- raw.position.map(_.raw)
        value <- raw.value.map(_.raw)
      } yield InstitutionalHolder(org, reportDate, pctHeld, position, value)

    private def mapMutualFundHolder(raw: OwnershipEntryRaw): Option[MutualFundHolder] =
      for {
        org <- raw.organization
        reportDate <- raw.reportDate.map(v => epochToLocalDate(v.raw))
        pctHeld <- raw.pctHeld.map(_.raw)
        position <- raw.position.map(_.raw)
        value <- raw.value.map(_.raw)
      } yield MutualFundHolder(org, reportDate, pctHeld, position, value)

    private def mapInsiderTransaction(raw: InsiderTransactionRaw): Option[InsiderTransaction] =
      for {
        filerName <- raw.filerName
        filerRelation <- raw.filerRelation
        transactionDate <- raw.startDate.map(v => epochToLocalDate(v.raw))
        shares <- raw.shares.map(_.raw)
      } yield InsiderTransaction(
        filerName = filerName,
        filerRelation = filerRelation,
        transactionDate = transactionDate,
        shares = shares,
        value = raw.value.map(_.raw),
        transactionText = raw.transactionText.getOrElse(""),
        ownershipType = raw.ownership.map(OwnershipType.fromString).getOrElse(OwnershipType.Direct),
        filerUrl = raw.filerUrl.filter(_.nonEmpty)
      )

    private def mapInsiderRosterEntry(raw: InsiderHolderRaw): Option[InsiderRosterEntry] =
      for {
        name <- raw.name
        relation <- raw.relation
      } yield InsiderRosterEntry(
        name = name,
        relation = relation,
        latestTransactionDate = raw.latestTransDate.map(v => epochToLocalDate(v.raw)),
        latestTransactionType = raw.transactionDescription,
        positionDirect = raw.positionDirect.map(_.raw),
        positionDirectDate = raw.positionDirectDate.map(v => epochToLocalDate(v.raw)),
        positionIndirect = raw.positionIndirect.map(_.raw),
        positionIndirectDate = raw.positionIndirectDate.map(v => epochToLocalDate(v.raw)),
        url = raw.url.filter(_.nonEmpty)
      )

    // --- Financial Statements Mapping ---

    private val DefaultCurrency = "USD"

    private def mapFinancialStatements(
        ticker: Ticker,
        result: YFinanceFinancialsResult
    ): Option[FinancialStatements] = {
      val byDate = result.byDate
      if (byDate.isEmpty) return None

      val currency = byDate.values.headOption.map(_.currencyCode).getOrElse(DefaultCurrency)

      Some(
        FinancialStatements(
          ticker = ticker,
          currency = currency,
          incomeStatements = extractIncomeStatements(result),
          balanceSheets = extractBalanceSheets(result),
          cashFlowStatements = extractCashFlowStatements(result)
        )
      )
    }

    private def extractIncomeStatements(result: YFinanceFinancialsResult): List[IncomeStatement] =
      result.byDate.toList.map { case (date, raw) =>
        raw.income
          .into[IncomeStatement]
          .withFieldConst(_.reportDate, date)
          .withFieldConst(_.periodType, raw.periodType)
          .withFieldConst(_.currencyCode, raw.currencyCode)
          .withFieldRenamed(_.depreciationAndAmortizationInIncomeStatement, _.depreciationAndAmortization)
          .withFieldRenamed(_.basicEPS, _.basicEps)
          .withFieldRenamed(_.dilutedEPS, _.dilutedEps)
          .withFieldRenamed(_.eBIT, _.ebit)
          .withFieldRenamed(_.eBITDA, _.ebitda)
          .transform
      }.sorted

    private def extractBalanceSheets(result: YFinanceFinancialsResult): List[BalanceSheet] =
      result.byDate.toList.map { case (date, raw) =>
        raw.balance
          .into[BalanceSheet]
          .withFieldConst(_.reportDate, date)
          .withFieldConst(_.periodType, raw.periodType)
          .withFieldConst(_.currencyCode, raw.currencyCode)
          .withFieldRenamed(_.otherShortTermInvestments, _.shortTermInvestments)
          .withFieldRenamed(_.netPPE, _.netPpe)
          .withFieldRenamed(_.grossPPE, _.grossPpe)
          .withFieldRenamed(_.longTermEquityInvestment, _.longTermInvestments)
          .withFieldRenamed(_.totalNonCurrentLiabilitiesNetMinorityInterest, _.totalNonCurrentLiabilities)
          .withFieldRenamed(_.totalLiabilitiesNetMinorityInterest, _.totalLiabilities)
          .withFieldRenamed(_.shareIssued, _.sharesIssued)
          .transform
      }.sorted

    private def extractCashFlowStatements(result: YFinanceFinancialsResult): List[CashFlowStatement] =
      result.byDate.toList.map { case (date, raw) =>
        raw.cashFlow
          .into[CashFlowStatement]
          .withFieldConst(_.reportDate, date)
          .withFieldConst(_.periodType, raw.periodType)
          .withFieldConst(_.currencyCode, raw.currencyCode)
          .withFieldRenamed(_.changeInPayable, _.changeInPayables)
          .withFieldRenamed(_.netPPEPurchaseAndSale, _.netPpePurchaseAndSale)
          .transform
      }.sorted

    // --- Analyst Data Mapping ---

    private def analystQuoteData(result: YFinanceAnalystResult): Option[AnalystQuoteData] =
      result.quoteSummary.result.headOption

    private def earningsTrendEntries(result: YFinanceAnalystResult): List[EarningsTrendEntryRaw] =
      analystQuoteData(result).flatMap(_.earningsTrend).flatMap(_.trend).getOrElse(List.empty)

    private def extractPriceTargets(result: YFinanceAnalystResult): Option[AnalystPriceTargets] =
      analystQuoteData(result).flatMap(_.financialData).flatMap(mapPriceTargets)

    private def extractRecommendations(result: YFinanceAnalystResult): List[RecommendationTrend] =
      analystQuoteData(result)
        .flatMap(_.recommendationTrend)
        .flatMap(_.trend)
        .getOrElse(List.empty)
        .flatMap(mapRecommendationTrend)
        .sorted

    private def extractUpgradeDowngrades(result: YFinanceAnalystResult): List[UpgradeDowngrade] =
      analystQuoteData(result)
        .flatMap(_.upgradeDowngradeHistory)
        .flatMap(_.history)
        .getOrElse(List.empty)
        .flatMap(mapUpgradeDowngrade)
        .sorted

    private def extractEarningsEstimates(result: YFinanceAnalystResult): List[EarningsEstimate] =
      earningsTrendEntries(result).flatMap(mapEarningsEstimate).sorted

    private def extractRevenueEstimates(result: YFinanceAnalystResult): List[RevenueEstimate] =
      earningsTrendEntries(result).flatMap(mapRevenueEstimate).sorted

    private def extractEpsTrends(result: YFinanceAnalystResult): List[EpsTrend] =
      earningsTrendEntries(result).flatMap(mapEpsTrend).sorted

    private def extractEpsRevisionsList(result: YFinanceAnalystResult): List[EpsRevisions] =
      earningsTrendEntries(result).flatMap(mapEpsRevisions).sorted

    private def extractEarningsHistoryEntries(result: YFinanceAnalystResult): List[EarningsHistory] =
      analystQuoteData(result)
        .flatMap(_.earningsHistory)
        .flatMap(_.history)
        .getOrElse(List.empty)
        .flatMap(mapEarningsHistoryEntry)
        .sorted

    private def extractGrowthEstimates(result: YFinanceAnalystResult): List[GrowthEstimates] = {
      val data = analystQuoteData(result)
      val indexTrend = data.flatMap(_.indexTrend)
      val indexSymbol = indexTrend.flatMap(_.symbol)
      val indexGrowthByPeriod: Map[String, Double] = indexTrend
        .flatMap(_.estimates)
        .getOrElse(List.empty)
        .flatMap(e => for { p <- e.period; g <- e.growth.map(_.raw) } yield p -> g)
        .toMap

      earningsTrendEntries(result).flatMap { entry =>
        entry.period.map { period =>
          GrowthEstimates(
            period = period,
            stockGrowth = entry.growth.map(_.raw),
            indexGrowth = indexGrowthByPeriod.get(period),
            indexSymbol = indexSymbol
          )
        }
      }.sorted
    }

    private def mapToAnalystData(result: YFinanceAnalystResult): Option[AnalystData] =
      analystQuoteData(result).map { _ =>
        AnalystData(
          priceTargets = extractPriceTargets(result),
          recommendations = extractRecommendations(result),
          upgradeDowngradeHistory = extractUpgradeDowngrades(result),
          earningsEstimates = extractEarningsEstimates(result),
          revenueEstimates = extractRevenueEstimates(result),
          epsTrends = extractEpsTrends(result),
          epsRevisions = extractEpsRevisionsList(result),
          earningsHistory = extractEarningsHistoryEntries(result),
          growthEstimates = extractGrowthEstimates(result)
        )
      }

    private def mapPriceTargets(raw: AnalystFinancialDataRaw): Option[AnalystPriceTargets] =
      for {
        currentPrice <- raw.currentPrice.map(_.raw)
        targetHigh <- raw.targetHighPrice.map(_.raw)
        targetLow <- raw.targetLowPrice.map(_.raw)
        targetMean <- raw.targetMeanPrice.map(_.raw)
        targetMedian <- raw.targetMedianPrice.map(_.raw)
        numAnalysts <- raw.numberOfAnalystOpinions.map(_.raw)
        recKey <- raw.recommendationKey
        recMean <- raw.recommendationMean.map(_.raw)
      } yield AnalystPriceTargets(
        currentPrice = currentPrice,
        targetHigh = targetHigh,
        targetLow = targetLow,
        targetMean = targetMean,
        targetMedian = targetMedian,
        numberOfAnalysts = numAnalysts,
        recommendationKey = recKey,
        recommendationMean = recMean
      )

    private def mapRecommendationTrend(raw: RecommendationTrendEntryRaw): Option[RecommendationTrend] =
      raw.period.map { period =>
        RecommendationTrend(
          period = period,
          strongBuy = raw.strongBuy.getOrElse(0),
          buy = raw.buy.getOrElse(0),
          hold = raw.hold.getOrElse(0),
          sell = raw.sell.getOrElse(0),
          strongSell = raw.strongSell.getOrElse(0)
        )
      }

    private def mapUpgradeDowngrade(raw: UpgradeDowngradeEntryRaw): Option[UpgradeDowngrade] =
      for {
        epochDate <- raw.epochGradeDate
        firm <- raw.firm
        toGrade <- raw.toGrade
        action <- raw.action
      } yield UpgradeDowngrade(
        date = epochToLocalDate(epochDate),
        firm = firm,
        toGrade = toGrade,
        fromGrade = raw.fromGrade.filter(_.nonEmpty),
        action = UpgradeDowngradeAction.fromString(action)
      )

    private def mapEarningsEstimate(raw: EarningsTrendEntryRaw): Option[EarningsEstimate] =
      raw.period.map { period =>
        EarningsEstimate(
          period = period,
          endDate = raw.endDate,
          avg = raw.earningsEstimate.flatMap(_.avg).map(_.raw),
          low = raw.earningsEstimate.flatMap(_.low).map(_.raw),
          high = raw.earningsEstimate.flatMap(_.high).map(_.raw),
          yearAgoEps = raw.earningsEstimate.flatMap(_.yearAgoEps).map(_.raw),
          numberOfAnalysts = raw.earningsEstimate.flatMap(_.numberOfAnalysts).map(_.raw),
          growth = raw.earningsEstimate.flatMap(_.growth).map(_.raw)
        )
      }

    private def mapRevenueEstimate(raw: EarningsTrendEntryRaw): Option[RevenueEstimate] =
      raw.period.map { period =>
        RevenueEstimate(
          period = period,
          endDate = raw.endDate,
          avg = raw.revenueEstimate.flatMap(_.avg).map(_.raw),
          low = raw.revenueEstimate.flatMap(_.low).map(_.raw),
          high = raw.revenueEstimate.flatMap(_.high).map(_.raw),
          numberOfAnalysts = raw.revenueEstimate.flatMap(_.numberOfAnalysts).map(_.raw),
          yearAgoRevenue = raw.revenueEstimate.flatMap(_.yearAgoRevenue).map(_.raw),
          growth = raw.revenueEstimate.flatMap(_.growth).map(_.raw)
        )
      }

    private def mapEpsTrend(raw: EarningsTrendEntryRaw): Option[EpsTrend] =
      raw.period.map { period =>
        EpsTrend(
          period = period,
          current = raw.epsTrend.flatMap(_.current).map(_.raw),
          sevenDaysAgo = raw.epsTrend.flatMap(_.sevenDaysAgo).map(_.raw),
          thirtyDaysAgo = raw.epsTrend.flatMap(_.thirtyDaysAgo).map(_.raw),
          sixtyDaysAgo = raw.epsTrend.flatMap(_.sixtyDaysAgo).map(_.raw),
          ninetyDaysAgo = raw.epsTrend.flatMap(_.ninetyDaysAgo).map(_.raw)
        )
      }

    private def mapEpsRevisions(raw: EarningsTrendEntryRaw): Option[EpsRevisions] =
      raw.period.map { period =>
        EpsRevisions(
          period = period,
          upLast7Days = raw.epsRevisions.flatMap(_.upLast7days).map(_.raw),
          upLast30Days = raw.epsRevisions.flatMap(_.upLast30days).map(_.raw),
          downLast30Days = raw.epsRevisions.flatMap(_.downLast30days).map(_.raw),
          downLast90Days = raw.epsRevisions.flatMap(_.downLast90days).map(_.raw)
        )
      }

    private def mapEarningsHistoryEntry(raw: EarningsHistoryEntryRaw): Option[EarningsHistory] =
      for {
        quarter <- raw.quarter.map(v => epochToLocalDate(v.raw))
        period <- raw.period
      } yield EarningsHistory(
        quarter = quarter,
        period = period,
        epsActual = raw.epsActual.map(_.raw),
        epsEstimate = raw.epsEstimate.map(_.raw),
        epsDifference = raw.epsDifference.map(_.raw),
        surprisePercent = raw.surprisePercent.map(_.raw)
      )
  }
}
