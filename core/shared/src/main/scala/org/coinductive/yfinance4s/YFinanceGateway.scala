package org.coinductive.yfinance4s

import cats.effect.{Async, Resource, Sync}
import cats.syntax.show.*
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.internal.*
import retry.{RetryPolicies, RetryPolicy, Sleep}
import sttp.client3.{SttpBackend, UriContext, basicRequest}

import java.time.{ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.FiniteDuration

sealed trait YFinanceGateway[F[_]] {
  def getChart(ticker: Ticker, interval: Interval, range: Range): F[YFinanceQueryResult]

  def getChart(
      ticker: Ticker,
      interval: Interval,
      since: ZonedDateTime,
      until: ZonedDateTime
  ): F[YFinanceQueryResult]

  def getOptions(ticker: Ticker, credentials: YFinanceCredentials): F[YFinanceOptionsResult]

  def getOptions(ticker: Ticker, expiration: Long, credentials: YFinanceCredentials): F[YFinanceOptionsResult]

  def getHolders(ticker: Ticker, credentials: YFinanceCredentials): F[YFinanceHoldersResult]

  def getFinancials(ticker: Ticker, frequency: Frequency, statementType: String = "all"): F[YFinanceFinancialsResult]

  def getAnalystData(ticker: Ticker, credentials: YFinanceCredentials): F[YFinanceAnalystResult]

  def search(query: String, quotesCount: Int, newsCount: Int, enableFuzzyQuery: Boolean): F[YFinanceSearchResult]

  def screenCustom(body: String, credentials: YFinanceCredentials): F[YFinanceScreenerResult]

  def screenPredefined(screenId: String, count: Int): F[YFinanceScreenerResult]

  def getSectorData(sectorKey: SectorKey, credentials: YFinanceCredentials): F[YFinanceSectorResult]

  def getIndustryData(industryKey: IndustryKey, credentials: YFinanceCredentials): F[YFinanceIndustryResult]

  def getMarketSummary(region: MarketRegion, credentials: YFinanceCredentials): F[YFinanceMarketSummaryResult]

  def getMarketStatus(region: MarketRegion, credentials: YFinanceCredentials): F[YFinanceMarketStatusResult]

  def getMarketTrending(
      region: MarketRegion,
      count: Int,
      credentials: YFinanceCredentials
  ): F[YFinanceTrendingResult]

  def postVisualization(body: String, credentials: YFinanceCredentials): F[YFinanceCalendarResult]
}

private object YFinanceGateway {

  def resource[F[_]: Async](
      connectTimeout: FiniteDuration,
      readTimeout: FiniteDuration,
      retries: Int
  ): Resource[F, YFinanceGateway[F]] =
    PlatformSttpBackend.resource[F](connectTimeout, readTimeout).map(apply[F](retries, _))

  def apply[F[_]: Sync: Sleep](retries: Int, sttpBackend: SttpBackend[F, Any]): YFinanceGateway[F] = {
    val retryPolicy = RetryPolicies.limitRetries(retries)
    new YFinanceGatewayImpl[F](sttpBackend, retryPolicy)
  }

  private final class YFinanceGatewayImpl[F[_]](
      protected val sttpBackend: SttpBackend[F, Any],
      protected val retryPolicy: RetryPolicy[F]
  )(implicit
      protected val F: Sync[F],
      protected val S: Sleep[F]
  ) extends HTTPBase[F]
      with YFinanceGateway[F] {

    private val ChartApiEndpoint = uri"https://query1.finance.yahoo.com/v8/finance/chart/"
    private val OptionsApiEndpoint = uri"https://query1.finance.yahoo.com/v7/finance/options/"
    private val QuoteSummaryEndpoint = uri"https://query1.finance.yahoo.com/v10/finance/quoteSummary/"

    private val HoldersModules =
      "majorHoldersBreakdown,institutionOwnership,fundOwnership,insiderTransactions,insiderHolders"

    private val AnalystModules =
      "financialData,recommendationTrend,upgradeDowngradeHistory,earningsTrend,earningsHistory,indexTrend"

    private val FinancialsEndpoint =
      uri"https://query2.finance.yahoo.com/ws/fundamentals-timeseries/v1/finance/timeseries/"

    private val SearchEndpoint = uri"https://query2.finance.yahoo.com/v1/finance/search"
    private val ScreenerEndpoint = uri"https://query1.finance.yahoo.com/v1/finance/screener"
    private val PredefinedScreenerEndpoint =
      uri"https://query1.finance.yahoo.com/v1/finance/screener/predefined/saved"
    private val DefaultQuotesQueryId = "tss_match_phrase_query"
    private val DefaultNewsQueryId = "news_cie_vespa"

    private val SectorsEndpoint = uri"https://query1.finance.yahoo.com/v1/finance/sectors/"
    private val IndustriesEndpoint = uri"https://query1.finance.yahoo.com/v1/finance/industries/"

    private val MarketSummaryEndpoint = uri"https://query1.finance.yahoo.com/v6/finance/quote/marketSummary"
    private val MarketTimeEndpoint = uri"https://query1.finance.yahoo.com/v6/finance/markettime"
    private val TrendingEndpoint = uri"https://query1.finance.yahoo.com/v1/finance/trending/"

    private val VisualizationEndpoint = uri"https://query1.finance.yahoo.com/v1/finance/visualization"

    private val VisualizationQueryParams = Map(
      "lang" -> "en-US",
      "region" -> "US"
    )

    private val MarketSummaryFields = List(
      "shortName",
      "fullExchangeName",
      "exchange",
      "currency",
      "marketState",
      "exchangeTimezoneName",
      "regularMarketPrice",
      "regularMarketChange",
      "regularMarketChangePercent",
      "regularMarketTime",
      "previousClose"
    ).mkString(",")

    private val MarketBaseParams = Map(
      "formatted" -> "true",
      "lang" -> "en-US"
    )

    private val DomainQueryParams = Map(
      "formatted" -> "true",
      "withReturns" -> "true",
      "lang" -> "en-US",
      "region" -> "US"
    )

    private val ScreenerQueryParams = Map(
      "corsDomain" -> "finance.yahoo.com",
      "formatted" -> "false",
      "lang" -> "en-US",
      "region" -> "US"
    )

    def getChart(ticker: Ticker, interval: Interval, range: Range): F[YFinanceQueryResult] = {
      val req =
        basicRequest.get(
          ChartApiEndpoint
            .addPath(ticker.show)
            .withParams(("interval", interval.show), ("range", range.show), ("events", "div,split"))
        )

      sendRequest(req, parseAs[YFinanceQueryResult]("chart"))
    }

    def getChart(
        ticker: Ticker,
        interval: Interval,
        since: ZonedDateTime,
        until: ZonedDateTime
    ): F[YFinanceQueryResult] = {
      val req =
        basicRequest.get(
          ChartApiEndpoint
            .addPath(ticker.show)
            .withParams(
              ("interval", interval.show),
              ("period1", since.toEpochSecond.show),
              ("period2", until.toEpochSecond.show),
              ("events", "div,split")
            )
        )

      sendRequest(req, parseAs[YFinanceQueryResult]("chart"))
    }

    def getOptions(ticker: Ticker, credentials: YFinanceCredentials): F[YFinanceOptionsResult] = {
      val req = basicRequest
        .get(
          OptionsApiEndpoint
            .addPath(ticker.show)
            .withParams(("crumb", credentials.crumb))
        )
        .headers(YFinanceAuth.apiHeaders *)
        .header("Cookie", credentials.cookies.mkString("; "))

      sendRequest(req, parseAs[YFinanceOptionsResult]("options"))
    }

    def getOptions(ticker: Ticker, expiration: Long, credentials: YFinanceCredentials): F[YFinanceOptionsResult] = {
      val req = basicRequest
        .get(
          OptionsApiEndpoint
            .addPath(ticker.show)
            .withParams(("date", expiration.toString), ("crumb", credentials.crumb))
        )
        .headers(YFinanceAuth.apiHeaders *)
        .header("Cookie", credentials.cookies.mkString("; "))

      sendRequest(req, parseAs[YFinanceOptionsResult]("options"))
    }

    def getHolders(ticker: Ticker, credentials: YFinanceCredentials): F[YFinanceHoldersResult] = {
      val req = basicRequest
        .get(
          QuoteSummaryEndpoint
            .addPath(ticker.show)
            .withParams(
              ("modules", HoldersModules),
              ("corsDomain", "finance.yahoo.com"),
              ("crumb", credentials.crumb)
            )
        )
        .headers(YFinanceAuth.apiHeaders *)
        .header("Cookie", credentials.cookies.mkString("; "))

      sendRequest(req, parseAs[YFinanceHoldersResult]("holders"))
    }

    def getFinancials(
        ticker: Ticker,
        frequency: Frequency,
        statementType: String
    ): F[YFinanceFinancialsResult] = {
      val keys = statementType match {
        case "income"        => IncomeStatement.apiKeys
        case "balance-sheet" => BalanceSheet.apiKeys
        case "cash-flow"     => CashFlowStatement.apiKeys
        case _               => IncomeStatement.apiKeys ++ BalanceSheet.apiKeys ++ CashFlowStatement.apiKeys
      }

      val typeParam = keys.map(k => s"${frequency.apiValue}$k").mkString(",")
      val now = ZonedDateTime.now(ZoneOffset.UTC)
      val startDate = now.minusYears(10)

      val req = basicRequest.get(
        FinancialsEndpoint
          .addPath(ticker.show)
          .withParams(
            ("symbol", ticker.show),
            ("type", typeParam),
            ("period1", startDate.toEpochSecond.toString),
            ("period2", now.toEpochSecond.toString)
          )
      )

      sendRequest(req, parseAs[YFinanceFinancialsResult]("financials"))
    }

    def getAnalystData(ticker: Ticker, credentials: YFinanceCredentials): F[YFinanceAnalystResult] = {
      val req = basicRequest
        .get(
          QuoteSummaryEndpoint
            .addPath(ticker.show)
            .withParams(
              ("modules", AnalystModules),
              ("corsDomain", "finance.yahoo.com"),
              ("crumb", credentials.crumb)
            )
        )
        .headers(YFinanceAuth.apiHeaders *)
        .header("Cookie", credentials.cookies.mkString("; "))

      sendRequest(req, parseAs[YFinanceAnalystResult]("analyst"))
    }

    def search(
        query: String,
        quotesCount: Int,
        newsCount: Int,
        enableFuzzyQuery: Boolean
    ): F[YFinanceSearchResult] = {
      val req = basicRequest.get(
        SearchEndpoint.withParams(
          ("q", query),
          ("quotesCount", quotesCount.toString),
          ("newsCount", newsCount.toString),
          ("enableFuzzyQuery", enableFuzzyQuery.toString),
          ("quotesQueryId", DefaultQuotesQueryId),
          ("newsQueryId", DefaultNewsQueryId),
          ("listsCount", quotesCount.toString),
          ("enableCb", "true"),
          ("enableNavLinks", "false"),
          ("enableResearchReports", "false"),
          ("enableCulturalAssets", "false"),
          ("recommendedCount", quotesCount.toString)
        )
      )

      sendRequest(req, parseAs[YFinanceSearchResult]("search"))
    }

    def screenCustom(body: String, credentials: YFinanceCredentials): F[YFinanceScreenerResult] = {
      val params = ScreenerQueryParams ++ Map("crumb" -> credentials.crumb)
      val req = basicRequest
        .post(ScreenerEndpoint.withParams(params))
        .body(body)
        .contentType("application/json")
        .headers(YFinanceAuth.apiHeaders *)
        .header("Cookie", credentials.cookies.mkString("; "))

      sendRequest(req, parseAs[YFinanceScreenerResult]("screener"))
    }

    def screenPredefined(screenId: String, count: Int): F[YFinanceScreenerResult] = {
      val params = ScreenerQueryParams ++ Map(
        "scrIds" -> screenId,
        "count" -> count.toString
      )
      val req = basicRequest.get(PredefinedScreenerEndpoint.withParams(params))

      sendRequest(req, parseAs[YFinanceScreenerResult]("screener"))
    }

    def getSectorData(sectorKey: SectorKey, credentials: YFinanceCredentials): F[YFinanceSectorResult] = {
      val req = basicRequest
        .get(
          SectorsEndpoint
            .addPath(sectorKey.show)
            .withParams(DomainQueryParams ++ Map("crumb" -> credentials.crumb))
        )
        .headers(YFinanceAuth.apiHeaders *)
        .header("Cookie", credentials.cookies.mkString("; "))
      sendRequest(req, parseAs[YFinanceSectorResult]("sector"))
    }

    def getIndustryData(industryKey: IndustryKey, credentials: YFinanceCredentials): F[YFinanceIndustryResult] = {
      val req = basicRequest
        .get(
          IndustriesEndpoint
            .addPath(industryKey.show)
            .withParams(DomainQueryParams ++ Map("crumb" -> credentials.crumb))
        )
        .headers(YFinanceAuth.apiHeaders *)
        .header("Cookie", credentials.cookies.mkString("; "))
      sendRequest(req, parseAs[YFinanceIndustryResult]("industry"))
    }

    def getMarketSummary(
        region: MarketRegion,
        credentials: YFinanceCredentials
    ): F[YFinanceMarketSummaryResult] = {
      val params = MarketBaseParams ++ Map(
        "fields" -> MarketSummaryFields,
        "market" -> region.show,
        "crumb" -> credentials.crumb
      )
      val req = basicRequest
        .get(MarketSummaryEndpoint.withParams(params))
        .headers(YFinanceAuth.apiHeaders *)
        .header("Cookie", credentials.cookies.mkString("; "))

      sendRequest(req, parseAs[YFinanceMarketSummaryResult]("market summary"))
    }

    def getMarketStatus(
        region: MarketRegion,
        credentials: YFinanceCredentials
    ): F[YFinanceMarketStatusResult] = {
      val params = MarketBaseParams ++ Map(
        "key" -> "finance",
        "market" -> region.show,
        "crumb" -> credentials.crumb
      )
      val req = basicRequest
        .get(MarketTimeEndpoint.withParams(params))
        .headers(YFinanceAuth.apiHeaders *)
        .header("Cookie", credentials.cookies.mkString("; "))

      sendRequest(req, parseAs[YFinanceMarketStatusResult]("market status"))
    }

    def getMarketTrending(
        region: MarketRegion,
        count: Int,
        credentials: YFinanceCredentials
    ): F[YFinanceTrendingResult] = {
      val params = MarketBaseParams ++ Map(
        "count" -> count.toString,
        "crumb" -> credentials.crumb
      )
      val req = basicRequest
        .get(TrendingEndpoint.addPath(region.show).withParams(params))
        .headers(YFinanceAuth.apiHeaders *)
        .header("Cookie", credentials.cookies.mkString("; "))

      sendRequest(req, parseAs[YFinanceTrendingResult]("trending"))
    }

    def postVisualization(body: String, credentials: YFinanceCredentials): F[YFinanceCalendarResult] = {
      val params = VisualizationQueryParams ++ Map("crumb" -> credentials.crumb)
      val req = basicRequest
        .post(VisualizationEndpoint.withParams(params))
        .body(body)
        .contentType("application/json")
        .headers(YFinanceAuth.apiHeaders *)
        .header("Cookie", credentials.cookies.mkString("; "))

      sendRequest(req, parseAs[YFinanceCalendarResult]("calendar"))
    }

  }

}
