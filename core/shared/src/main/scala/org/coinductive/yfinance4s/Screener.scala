package org.coinductive.yfinance4s

import cats.Monad
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import io.circe.Json
import io.circe.syntax.*
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.internal.*

/** Algebra for stock and fund screening via Yahoo Finance.
  *
  * Build queries with [[models.EquityQuery]] or [[models.FundQuery]], then run them:
  * {{{
  * val query = EquityQuery.and(
  *   EquityQuery.gt("percentchange", 3),
  *   EquityQuery.eq("region", "us"),
  *   EquityQuery.gte("intradaymarketcap", 2000000000L)
  * )
  * client.screener.screenEquities(query)
  * }}}
  *
  * Or use predefined screens:
  * {{{
  * client.screener.screenPredefined(PredefinedScreen.DayGainers)
  * }}}
  */
trait Screener[F[_]] {

  /** Runs a custom equity (stock) screener query. */
  def screenEquities(
      query: ScreenerQuery,
      config: ScreenerConfig = ScreenerConfig.Default
  ): F[ScreenerResult]

  /** Runs a custom mutual fund screener query. */
  def screenFunds(
      query: ScreenerQuery,
      config: ScreenerConfig = ScreenerConfig.Default
  ): F[ScreenerResult]

  /** Runs a predefined screener query by name. */
  def screenPredefined(
      screen: PredefinedScreen,
      count: Int = Screener.DefaultCount
  ): F[ScreenerResult]
}

private[yfinance4s] object Screener {

  val DefaultCount = 25

  def apply[F[_]: Monad](gateway: YFinanceGateway[F], auth: YFinanceAuth[F]): Screener[F] =
    new ScreenerImpl(gateway, auth)

  private final class ScreenerImpl[F[_]: Monad](gateway: YFinanceGateway[F], auth: YFinanceAuth[F])
      extends Screener[F] {

    private val EquityQuoteType = "EQUITY"
    private val FundQuoteType = "MUTUALFUND"
    private val DefaultUserId = ""
    private val DefaultUserIdType = "guid"

    def screenEquities(query: ScreenerQuery, config: ScreenerConfig): F[ScreenerResult] =
      screenCustom(query, EquityQuoteType, config)

    def screenFunds(query: ScreenerQuery, config: ScreenerConfig): F[ScreenerResult] =
      screenCustom(query, FundQuoteType, config)

    def screenPredefined(screen: PredefinedScreen, count: Int): F[ScreenerResult] =
      gateway.screenPredefined(screen.screenId, count).map(mapScreenerResult)

    // --- Private Helpers ---

    private def screenCustom(query: ScreenerQuery, quoteType: String, config: ScreenerConfig): F[ScreenerResult] = {
      val body = buildRequestBody(query, quoteType, config)
      auth.getCredentials.flatMap { credentials =>
        gateway.screenCustom(body, credentials).map(mapScreenerResult)
      }
    }

    private def buildRequestBody(query: ScreenerQuery, quoteType: String, config: ScreenerConfig): String = {
      val json = Json.obj(
        "offset" -> Json.fromInt(config.offset),
        "size" -> Json.fromInt(config.count),
        "sortField" -> Json.fromString(config.sortField),
        "sortType" -> Json.fromString(config.sortOrder.apiValue),
        "userId" -> Json.fromString(DefaultUserId),
        "userIdType" -> Json.fromString(DefaultUserIdType),
        "quoteType" -> Json.fromString(quoteType),
        "query" -> query.asJson
      )
      json.noSpaces
    }

    private def mapScreenerResult(raw: YFinanceScreenerResult): ScreenerResult =
      ScreenerResult(
        quotes = raw.quotes.flatMap(mapScreenerQuote),
        total = raw.total
      )

    private def mapScreenerQuote(raw: ScreenerQuoteRaw): Option[ScreenerQuote] =
      raw.symbol.map { sym =>
        ScreenerQuote(
          symbol = sym,
          shortName = raw.shortName,
          longName = raw.longName,
          quoteType = raw.quoteType,
          exchange = raw.exchange,
          exchangeDisplay = raw.exchangeDisp,
          sector = raw.sectorDisp.orElse(raw.sector),
          industry = raw.industryDisp.orElse(raw.industry),
          regularMarketPrice = raw.regularMarketPrice,
          regularMarketChange = raw.regularMarketChange,
          regularMarketChangePercent = raw.regularMarketChangePercent,
          regularMarketVolume = raw.regularMarketVolume,
          marketCap = raw.marketCap,
          trailingPE = raw.trailingPE,
          forwardPE = raw.forwardPE,
          priceToBook = raw.priceToBook,
          fiftyTwoWeekHigh = raw.fiftyTwoWeekHigh,
          fiftyTwoWeekLow = raw.fiftyTwoWeekLow,
          dividendYield = raw.dividendYield,
          epsTrailingTwelveMonths = raw.epsTrailingTwelveMonths,
          averageDailyVolume3Month = raw.averageDailyVolume3Month
        )
      }
  }
}
