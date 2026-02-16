package org.coinductive.yfinance4s

import cats.effect.{Async, Resource, Sync}
import cats.syntax.show.*
import io.circe.parser.decode
import org.coinductive.yfinance4s.models.*
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

    def getChart(ticker: Ticker, interval: Interval, range: Range): F[YFinanceQueryResult] = {
      val req =
        basicRequest.get(
          ChartApiEndpoint
            .addPath(ticker.show)
            .withParams(("interval", interval.show), ("range", range.show), ("events", "div,split"))
        )

      sendRequest(req, parseChartContent)
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

      sendRequest(req, parseChartContent)
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

      sendRequest(req, parseOptionsContent)
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

      sendRequest(req, parseOptionsContent)
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

      sendRequest(req, parseHoldersContent)
    }

    private def parseChartContent(content: String): F[YFinanceQueryResult] = {
      decode[YFinanceQueryResult](content)
        .fold(
          e => F.raiseError(new Exception(s"Failed to parse chart response: ${e.getMessage}")),
          F.pure
        )
    }

    private def parseOptionsContent(content: String): F[YFinanceOptionsResult] = {
      decode[YFinanceOptionsResult](content)
        .fold(
          e => F.raiseError(new Exception(s"Failed to parse options response: ${e.getMessage}")),
          F.pure
        )
    }

    private def parseHoldersContent(content: String): F[YFinanceHoldersResult] = {
      decode[YFinanceHoldersResult](content)
        .fold(
          e => F.raiseError(new Exception(s"Failed to parse holders response: ${e.getMessage}")),
          F.pure
        )
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

      sendRequest(req, parseFinancialsContent)
    }

    private def parseFinancialsContent(content: String): F[YFinanceFinancialsResult] =
      decode[YFinanceFinancialsResult](content)
        .fold(
          e => F.raiseError(new Exception(s"Failed to parse financials response: ${e.getMessage}")),
          F.pure
        )

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

      sendRequest(req, parseAnalystContent)
    }

    private def parseAnalystContent(content: String): F[YFinanceAnalystResult] =
      decode[YFinanceAnalystResult](content)
        .fold(
          e => F.raiseError(new Exception(s"Failed to parse analyst response: ${e.getMessage}")),
          F.pure
        )

  }

}
