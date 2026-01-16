package org.coinductive.yfinance4s

import cats.Monad
import cats.effect.{Async, Resource}
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.YFinanceQueryResult.InstrumentData

import java.time.{Instant, LocalDate, ZoneOffset, ZonedDateTime}

final class YFinanceClient[F[_]: Monad] private (
    gateway: YFinanceGateway[F],
    scrapper: YFinanceScrapper[F],
    auth: YFinanceAuth[F]
) {

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

  /** Retrieves all available option expiration dates for a ticker.
    *
    * @param ticker
    *   The stock ticker symbol
    * @return
    *   List of available expiration dates, sorted chronologically
    */
  def getOptionExpirations(ticker: Ticker): F[Option[List[LocalDate]]] =
    auth.getCredentials.flatMap { credentials =>
      gateway.getOptions(ticker, credentials).map(extractExpirations)
    }

  /** Retrieves the option chain for a specific expiration date.
    *
    * @param ticker
    *   The stock ticker symbol
    * @param expirationDate
    *   The desired expiration date
    * @return
    *   The option chain for that expiration, or None if not available
    */
  def getOptionChain(ticker: Ticker, expirationDate: LocalDate): F[Option[OptionChain]] = {
    val epochSeconds = expirationDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond
    auth.getCredentials.flatMap { credentials =>
      gateway.getOptions(ticker, epochSeconds, credentials).map(extractOptionChain(_, expirationDate))
    }
  }

  /** Retrieves the nearest expiration's option chain along with all available expirations. This is more efficient than
    * calling getOptionExpirations + getOptionChain separately.
    *
    * @param ticker
    *   The stock ticker symbol
    * @return
    *   Full option chain data including all available expirations
    */
  def getFullOptionChain(ticker: Ticker): F[Option[FullOptionChain]] =
    auth.getCredentials.flatMap { credentials =>
      gateway.getOptions(ticker, credentials).map(mapToFullOptionChain)
    }

  /** Retrieves major holders breakdown for a ticker.
    *
    * @param ticker
    *   The stock ticker symbol
    * @return
    *   Breakdown of insider vs institutional ownership percentages
    */
  def getMajorHolders(ticker: Ticker): F[Option[MajorHolders]] =
    auth.getCredentials.flatMap { credentials =>
      gateway.getHolders(ticker, credentials).map(extractMajorHolders)
    }

  /** Retrieves top institutional holders for a ticker.
    *
    * @param ticker
    *   The stock ticker symbol
    * @return
    *   List of top institutional holders, sorted by percentage held (descending)
    */
  def getInstitutionalHolders(ticker: Ticker): F[List[InstitutionalHolder]] =
    auth.getCredentials.flatMap { credentials =>
      gateway.getHolders(ticker, credentials).map(extractInstitutionalHolders)
    }

  /** Retrieves top mutual fund holders for a ticker.
    *
    * @param ticker
    *   The stock ticker symbol
    * @return
    *   List of top mutual fund holders, sorted by percentage held (descending)
    */
  def getMutualFundHolders(ticker: Ticker): F[List[MutualFundHolder]] =
    auth.getCredentials.flatMap { credentials =>
      gateway.getHolders(ticker, credentials).map(extractMutualFundHolders)
    }

  /** Retrieves recent insider transactions for a ticker.
    *
    * @param ticker
    *   The stock ticker symbol
    * @return
    *   List of insider transactions, sorted by date (most recent first)
    */
  def getInsiderTransactions(ticker: Ticker): F[List[InsiderTransaction]] =
    auth.getCredentials.flatMap { credentials =>
      gateway.getHolders(ticker, credentials).map(extractInsiderTransactions)
    }

  /** Retrieves insider roster (current positions) for a ticker.
    *
    * @param ticker
    *   The stock ticker symbol
    * @return
    *   List of insider roster entries, sorted by position size (descending)
    */
  def getInsiderRoster(ticker: Ticker): F[List[InsiderRosterEntry]] =
    auth.getCredentials.flatMap { credentials =>
      gateway.getHolders(ticker, credentials).map(extractInsiderRoster)
    }

  /** Retrieves comprehensive holders data for a ticker.
    *
    * @param ticker
    *   The stock ticker symbol
    * @return
    *   Complete holders data including major holders, institutional/fund holders, and insider information
    */
  def getHoldersData(ticker: Ticker): F[Option[HoldersData]] =
    auth.getCredentials.flatMap { credentials =>
      gateway.getHolders(ticker, credentials).map(mapToHoldersData)
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

  private def rawToContract(raw: OptionContractRaw, optionType: OptionType, expiration: LocalDate): OptionContract =
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

}

object YFinanceClient {

  def resource[F[_]: Async](config: YFinanceClientConfig): Resource[F, YFinanceClient[F]] = {
    for {
      gateway <- YFinanceGateway.resource[F](config.connectTimeout, config.readTimeout, config.retries)
      scrapper <- YFinanceScrapper.resource[F](config.connectTimeout, config.readTimeout, config.retries)
      auth <- YFinanceAuth.resource[F](config.connectTimeout, config.readTimeout, config.retries)
    } yield new YFinanceClient(gateway, scrapper, auth)
  }

}
