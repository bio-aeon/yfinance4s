package org.coinductive.yfinance4s.unit

import cats.Id
import cats.data.NonEmptyList
import munit.FunSuite
import org.coinductive.yfinance4s.*
import org.coinductive.yfinance4s.models.*

import java.time.{LocalDate, ZonedDateTime}

class TickersSpec extends FunSuite {

  /** Stub client for unit tests. All methods throw NotImplementedError but are never called since unit tests only
    * exercise pure operations on Tickers (add, remove, contains, etc.).
    */
  private val stubCharts: Charts[Id] = new Charts[Id] {
    def getChart(ticker: Ticker, interval: Interval, range: Range): Option[ChartResult] = ???
    def getChart(ticker: Ticker, interval: Interval, since: ZonedDateTime, until: ZonedDateTime): Option[ChartResult] =
      ???
    def getStock(ticker: Ticker): Option[StockResult] = ???
    def getDividends(ticker: Ticker, interval: Interval, range: Range): Option[List[DividendEvent]] = ???
    def getDividends(
        ticker: Ticker,
        interval: Interval,
        since: ZonedDateTime,
        until: ZonedDateTime
    ): Option[List[DividendEvent]] = ???
    def getSplits(ticker: Ticker, interval: Interval, range: Range): Option[List[SplitEvent]] = ???
    def getSplits(
        ticker: Ticker,
        interval: Interval,
        since: ZonedDateTime,
        until: ZonedDateTime
    ): Option[List[SplitEvent]] = ???
    def getCorporateActions(ticker: Ticker, interval: Interval, range: Range): Option[CorporateActions] = ???
    def getCorporateActions(
        ticker: Ticker,
        interval: Interval,
        since: ZonedDateTime,
        until: ZonedDateTime
    ): Option[CorporateActions] = ???
  }

  private val stubOptions: Options[Id] = new Options[Id] {
    def getOptionExpirations(ticker: Ticker): Option[List[LocalDate]] = ???
    def getOptionChain(ticker: Ticker, expirationDate: LocalDate): Option[OptionChain] = ???
    def getFullOptionChain(ticker: Ticker): Option[FullOptionChain] = ???
  }

  private val stubHolders: Holders[Id] = new Holders[Id] {
    def getMajorHolders(ticker: Ticker): Option[MajorHolders] = ???
    def getInstitutionalHolders(ticker: Ticker): List[InstitutionalHolder] = ???
    def getMutualFundHolders(ticker: Ticker): List[MutualFundHolder] = ???
    def getInsiderTransactions(ticker: Ticker): List[InsiderTransaction] = ???
    def getInsiderRoster(ticker: Ticker): List[InsiderRosterEntry] = ???
    def getHoldersData(ticker: Ticker): Option[HoldersData] = ???
  }

  private val stubFinancials: Financials[Id] = new Financials[Id] {
    def getFinancialStatements(ticker: Ticker, frequency: Frequency): Option[FinancialStatements] = ???
    def getIncomeStatements(ticker: Ticker, frequency: Frequency): List[IncomeStatement] = ???
    def getBalanceSheets(ticker: Ticker, frequency: Frequency): List[BalanceSheet] = ???
    def getCashFlowStatements(ticker: Ticker, frequency: Frequency): List[CashFlowStatement] = ???
  }

  private val stubAnalysts: Analysts[Id] = new Analysts[Id] {
    def getAnalystPriceTargets(ticker: Ticker): Option[AnalystPriceTargets] = ???
    def getRecommendations(ticker: Ticker): List[RecommendationTrend] = ???
    def getUpgradeDowngradeHistory(ticker: Ticker): List[UpgradeDowngrade] = ???
    def getEarningsEstimates(ticker: Ticker): List[EarningsEstimate] = ???
    def getRevenueEstimates(ticker: Ticker): List[RevenueEstimate] = ???
    def getEarningsHistory(ticker: Ticker): List[EarningsHistory] = ???
    def getGrowthEstimates(ticker: Ticker): List[GrowthEstimates] = ???
    def getAnalystData(ticker: Ticker): Option[AnalystData] = ???
  }

  private val client: YFinanceClient[Id] = new YFinanceClient[Id] {
    val charts: Charts[Id] = stubCharts
    val options: Options[Id] = stubOptions
    val holders: Holders[Id] = stubHolders
    val financials: Financials[Id] = stubFinancials
    val analysts: Analysts[Id] = stubAnalysts
    val screener: Screener[Id] = new Screener[Id] {
      def screenEquities(query: ScreenerQuery, config: ScreenerConfig): ScreenerResult = ???
      def screenFunds(query: ScreenerQuery, config: ScreenerConfig): ScreenerResult = ???
      def screenPredefined(screen: PredefinedScreen, count: Int): ScreenerResult = ???
    }
    val sectors: Sectors[Id] = new Sectors[Id] {
      def getSectorData(sectorKey: SectorKey): Option[SectorData] = ???
      def getSectorOverview(sectorKey: SectorKey): Option[SectorOverview] = ???
      def getTopETFs(sectorKey: SectorKey): List[SectorETF] = ???
      def getTopMutualFunds(sectorKey: SectorKey): List[SectorMutualFund] = ???
      def getIndustries(sectorKey: SectorKey): List[SectorIndustry] = ???
      def getTopCompanies(sectorKey: SectorKey): List[TopCompany] = ???
    }
    val industries: Industries[Id] = new Industries[Id] {
      def getIndustryData(industryKey: IndustryKey): IndustryData = ???
      def getIndustryOverview(industryKey: IndustryKey): Option[IndustryOverview] = ???
      def getSectorInfo(industryKey: IndustryKey): IndustrySectorInfo = ???
      def getTopCompanies(industryKey: IndustryKey): List[TopCompany] = ???
      def getTopPerformingCompanies(industryKey: IndustryKey): List[TopPerformingCompany] = ???
      def getTopGrowthCompanies(industryKey: IndustryKey): List[TopGrowthCompany] = ???
      def getResearchReports(industryKey: IndustryKey): List[ResearchReport] = ???
    }
    val markets: Markets[Id] = new Markets[Id] {
      def getSummary(region: MarketRegion): MarketSummary = ???
      def getStatus(region: MarketRegion): MarketStatus = ???
      def getTrending(region: MarketRegion, count: Int): List[TrendingTicker] = ???
      def getMarketSnapshot(region: MarketRegion): MarketSnapshot = ???
    }
    val calendars: Calendars[Id] = new Calendars[Id] {
      def getEarningsCalendar(start: LocalDate, end: LocalDate, config: CalendarConfig): List[EarningsEvent] = ???
      def getEarningsDates(ticker: Ticker, limit: Int, offset: Int): List[EarningsDate] = ???
    }
    def search(query: String, maxResults: Int, newsCount: Int, enableFuzzyQuery: Boolean): SearchResult = ???
    def lookupByISIN(isin: String): Option[Ticker] = ???
    def lookupAllByISIN(isin: String): List[QuoteSearchResult] = ???
  }

  // --- Factory Tests ---

  test("creates ticker collection from string varargs") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    assertEquals(t.size, 2)
    assertEquals(t.tickers, NonEmptyList.of(Ticker("AAPL"), Ticker("MSFT")))
  }

  test("deduplicates input tickers on creation") {
    val t = Tickers.of[Id](client, "AAPL", "AAPL", "MSFT", "MSFT")
    assertEquals(t.size, 2)
    assertEquals(t.tickers, NonEmptyList.of(Ticker("AAPL"), Ticker("MSFT")))
  }

  test("creates ticker collection from NonEmptyList") {
    val nel = NonEmptyList.of(Ticker("AAPL"), Ticker("GOOGL"))
    val t = Tickers[Id](client, nel)
    assertEquals(t.tickers, nel)
    assertEquals(t.size, 2)
  }

  test("creates single-ticker collection") {
    val t = Tickers.single[Id](client, Ticker("AAPL"))
    assertEquals(t.size, 1)
    assertEquals(t.tickers.head, Ticker("AAPL"))
  }

  test("defaults parallelism to 4") {
    val t = Tickers[Id](client, NonEmptyList.one(Ticker("AAPL")))
    assertEquals(t.parallelism, 4)
  }

  test("accepts custom parallelism") {
    val t = Tickers[Id](client, NonEmptyList.one(Ticker("AAPL")), parallelism = 8)
    assertEquals(t.parallelism, 8)
  }

  // --- Ticker List Manipulation Tests ---

  test("appends a new ticker") {
    val t = Tickers.of[Id](client, "AAPL")
    val t2 = t.add(Ticker("MSFT"))
    assertEquals(t2.size, 2)
    assert(t2.contains(Ticker("AAPL")))
    assert(t2.contains(Ticker("MSFT")))
  }

  test("ignores duplicate when adding existing ticker") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    val t2 = t.add(Ticker("AAPL"))
    assertEquals(t2.size, 2)
    assertEquals(t2.tickers, t.tickers)
  }

  test("appends multiple new tickers") {
    val t = Tickers.of[Id](client, "AAPL")
    val t2 = t.addAll(NonEmptyList.of(Ticker("MSFT"), Ticker("GOOGL")))
    assertEquals(t2.size, 3)
    assert(t2.contains(Ticker("AAPL")))
    assert(t2.contains(Ticker("MSFT")))
    assert(t2.contains(Ticker("GOOGL")))
  }

  test("deduplicates when adding multiple tickers") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    val t2 = t.addAll(NonEmptyList.of(Ticker("MSFT"), Ticker("GOOGL")))
    assertEquals(t2.size, 3)
    assertEquals(t2.tickers, NonEmptyList.of(Ticker("AAPL"), Ticker("MSFT"), Ticker("GOOGL")))
  }

  test("removes ticker from multi-element list") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    val result = t.remove(Ticker("AAPL"))
    assert(result.isDefined)
    assertEquals(result.get.size, 1)
    assertEquals(result.get.tickers.head, Ticker("MSFT"))
  }

  test("returns None when removing the only ticker") {
    val t = Tickers.single[Id](client, Ticker("AAPL"))
    val result = t.remove(Ticker("AAPL"))
    assertEquals(result, None)
  }

  test("returns unchanged list when removing absent ticker") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    val result = t.remove(Ticker("GOOGL"))
    assert(result.isDefined)
    assertEquals(result.get.tickers, t.tickers)
  }

  test("finds existing ticker in collection") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    assert(t.contains(Ticker("AAPL")))
    assert(t.contains(Ticker("MSFT")))
  }

  test("does not find absent ticker in collection") {
    val t = Tickers.of[Id](client, "AAPL")
    assert(!t.contains(Ticker("MSFT")))
  }

  test("reports correct collection size") {
    assertEquals(Tickers.single[Id](client, Ticker("AAPL")).size, 1)
    assertEquals(Tickers.of[Id](client, "AAPL", "MSFT", "GOOGL").size, 3)
  }

  test("extracts string symbols from tickers") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    assertEquals(t.symbols, NonEmptyList.of("AAPL", "MSFT"))
  }

  test("updates parallelism without mutating original") {
    val t = Tickers.of[Id](client, "AAPL")
    val t2 = t.withParallelism(8)
    assertEquals(t2.parallelism, 8)
    assertEquals(t.parallelism, 4) // original unchanged
  }

  test("preserves tickers when updating parallelism") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    val t2 = t.withParallelism(2)
    assertEquals(t2.tickers, t.tickers)
  }
}
