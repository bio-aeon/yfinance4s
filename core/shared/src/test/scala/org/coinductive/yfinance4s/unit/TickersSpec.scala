package org.coinductive.yfinance4s.unit

import cats.Id
import cats.data.NonEmptyList
import munit.FunSuite
import org.coinductive.yfinance4s.{Tickers, YFinanceClient}
import org.coinductive.yfinance4s.models.*

import java.time.{LocalDate, ZonedDateTime}

class TickersSpec extends FunSuite {

  /** Stub client for unit tests. All methods throw NotImplementedError but are never called since unit tests only
    * exercise pure operations on Tickers (add, remove, contains, etc.).
    */
  private val client: YFinanceClient[Id] = new YFinanceClient[Id] {
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
    def getOptionExpirations(ticker: Ticker): Option[List[LocalDate]] = ???
    def getOptionChain(ticker: Ticker, expirationDate: LocalDate): Option[OptionChain] = ???
    def getFullOptionChain(ticker: Ticker): Option[FullOptionChain] = ???
    def getMajorHolders(ticker: Ticker): Option[MajorHolders] = ???
    def getInstitutionalHolders(ticker: Ticker): List[InstitutionalHolder] = ???
    def getMutualFundHolders(ticker: Ticker): List[MutualFundHolder] = ???
    def getInsiderTransactions(ticker: Ticker): List[InsiderTransaction] = ???
    def getInsiderRoster(ticker: Ticker): List[InsiderRosterEntry] = ???
    def getHoldersData(ticker: Ticker): Option[HoldersData] = ???
    def getFinancialStatements(ticker: Ticker, frequency: Frequency): Option[FinancialStatements] = ???
    def getIncomeStatements(ticker: Ticker, frequency: Frequency): List[IncomeStatement] = ???
    def getBalanceSheets(ticker: Ticker, frequency: Frequency): List[BalanceSheet] = ???
    def getCashFlowStatements(ticker: Ticker, frequency: Frequency): List[CashFlowStatement] = ???
    def getAnalystPriceTargets(ticker: Ticker): Option[AnalystPriceTargets] = ???
    def getRecommendations(ticker: Ticker): List[RecommendationTrend] = ???
    def getUpgradeDowngradeHistory(ticker: Ticker): List[UpgradeDowngrade] = ???
    def getEarningsEstimates(ticker: Ticker): List[EarningsEstimate] = ???
    def getRevenueEstimates(ticker: Ticker): List[RevenueEstimate] = ???
    def getEarningsHistory(ticker: Ticker): List[EarningsHistory] = ???
    def getGrowthEstimates(ticker: Ticker): List[GrowthEstimates] = ???
    def getAnalystData(ticker: Ticker): Option[AnalystData] = ???
  }

  // --- Factory Tests ---

  test("Tickers.of should create from string varargs") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    assertEquals(t.size, 2)
    assertEquals(t.tickers, NonEmptyList.of(Ticker("AAPL"), Ticker("MSFT")))
  }

  test("Tickers.of should deduplicate input tickers") {
    val t = Tickers.of[Id](client, "AAPL", "AAPL", "MSFT", "MSFT")
    assertEquals(t.size, 2)
    assertEquals(t.tickers, NonEmptyList.of(Ticker("AAPL"), Ticker("MSFT")))
  }

  test("Tickers.apply should create from NonEmptyList") {
    val nel = NonEmptyList.of(Ticker("AAPL"), Ticker("GOOGL"))
    val t = Tickers[Id](client, nel)
    assertEquals(t.tickers, nel)
    assertEquals(t.size, 2)
  }

  test("Tickers.single should create with one ticker") {
    val t = Tickers.single[Id](client, Ticker("AAPL"))
    assertEquals(t.size, 1)
    assertEquals(t.tickers.head, Ticker("AAPL"))
  }

  test("Tickers.apply should use default parallelism of 4") {
    val t = Tickers[Id](client, NonEmptyList.one(Ticker("AAPL")))
    assertEquals(t.parallelism, 4)
  }

  test("Tickers.apply should accept custom parallelism") {
    val t = Tickers[Id](client, NonEmptyList.one(Ticker("AAPL")), parallelism = 8)
    assertEquals(t.parallelism, 8)
  }

  // --- Ticker List Manipulation Tests ---

  test("add should append a new ticker") {
    val t = Tickers.of[Id](client, "AAPL")
    val t2 = t.add(Ticker("MSFT"))
    assertEquals(t2.size, 2)
    assert(t2.contains(Ticker("AAPL")))
    assert(t2.contains(Ticker("MSFT")))
  }

  test("add should not duplicate an existing ticker") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    val t2 = t.add(Ticker("AAPL"))
    assertEquals(t2.size, 2)
    assertEquals(t2.tickers, t.tickers)
  }

  test("addAll should append multiple new tickers") {
    val t = Tickers.of[Id](client, "AAPL")
    val t2 = t.addAll(NonEmptyList.of(Ticker("MSFT"), Ticker("GOOGL")))
    assertEquals(t2.size, 3)
    assert(t2.contains(Ticker("AAPL")))
    assert(t2.contains(Ticker("MSFT")))
    assert(t2.contains(Ticker("GOOGL")))
  }

  test("addAll should deduplicate when adding") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    val t2 = t.addAll(NonEmptyList.of(Ticker("MSFT"), Ticker("GOOGL")))
    assertEquals(t2.size, 3)
    assertEquals(t2.tickers, NonEmptyList.of(Ticker("AAPL"), Ticker("MSFT"), Ticker("GOOGL")))
  }

  test("remove should return Some when ticker exists and list has multiple elements") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    val result = t.remove(Ticker("AAPL"))
    assert(result.isDefined)
    assertEquals(result.get.size, 1)
    assertEquals(result.get.tickers.head, Ticker("MSFT"))
  }

  test("remove should return None when removing the only ticker") {
    val t = Tickers.single[Id](client, Ticker("AAPL"))
    val result = t.remove(Ticker("AAPL"))
    assertEquals(result, None)
  }

  test("remove should return unchanged tickers when ticker not in list") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    val result = t.remove(Ticker("GOOGL"))
    assert(result.isDefined)
    assertEquals(result.get.tickers, t.tickers)
  }

  test("contains should return true for existing ticker") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    assert(t.contains(Ticker("AAPL")))
    assert(t.contains(Ticker("MSFT")))
  }

  test("contains should return false for non-existing ticker") {
    val t = Tickers.of[Id](client, "AAPL")
    assert(!t.contains(Ticker("MSFT")))
  }

  test("size should return number of tickers") {
    assertEquals(Tickers.single[Id](client, Ticker("AAPL")).size, 1)
    assertEquals(Tickers.of[Id](client, "AAPL", "MSFT", "GOOGL").size, 3)
  }

  test("symbols should return string representations") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    assertEquals(t.symbols, NonEmptyList.of("AAPL", "MSFT"))
  }

  test("withParallelism should return new instance with updated value") {
    val t = Tickers.of[Id](client, "AAPL")
    val t2 = t.withParallelism(8)
    assertEquals(t2.parallelism, 8)
    assertEquals(t.parallelism, 4) // original unchanged
  }

  test("withParallelism should preserve tickers") {
    val t = Tickers.of[Id](client, "AAPL", "MSFT")
    val t2 = t.withParallelism(2)
    assertEquals(t2.tickers, t.tickers)
  }
}
