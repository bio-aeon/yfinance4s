package org.coinductive.yfinance4s

import cats.Monad
import cats.data.NonEmptyList
import cats.effect.{Async, Concurrent, Resource}
import cats.effect.syntax.concurrent.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import org.coinductive.yfinance4s.Mapping.*
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.internal.*

import java.time.ZonedDateTime

/** Effectful Yahoo Finance client for fetching financial data.
  *
  * Provides domain-specific modules for retrieving historical price data, current quotes, corporate actions, options
  * chains, holders information, financial statements, and analyst data. All operations are wrapped in an effect type
  * `F[_]`.
  *
  * Obtain an instance via [[YFinanceClient.resource]].
  */
trait YFinanceClient[F[_]] {

  /** Historical chart data, stock quotes, and corporate actions. */
  def charts: Charts[F]

  /** Options chain data. */
  def options: Options[F]

  /** Holders and insider data. */
  def holders: Holders[F]

  /** Financial statements data. */
  def financials: Financials[F]

  /** Analyst data (price targets, recommendations, estimates, etc.). */
  def analysts: Analysts[F]

  /** Searches Yahoo Finance for tickers, companies, and news.
    *
    * @param query
    *   The search query (ticker symbol or company name)
    * @param maxResults
    *   Maximum number of quote results (default 8)
    * @param newsCount
    *   Number of news articles to include (default 8)
    * @param enableFuzzyQuery
    *   Enable fuzzy matching for typo tolerance (default false)
    * @return
    *   Search results containing matched quotes, news, and lists
    */
  def search(
      query: String,
      maxResults: Int = YFinanceClient.SearchDefaults.MaxResults,
      newsCount: Int = YFinanceClient.SearchDefaults.NewsCount,
      enableFuzzyQuery: Boolean = YFinanceClient.SearchDefaults.EnableFuzzyQuery
  ): F[SearchResult]

  // --- Multi-Ticker Downloads (concrete with default implementations) ---

  /** Downloads chart data for multiple tickers in parallel. Fails if any ticker fetch fails. */
  def downloadCharts(
      tickers: NonEmptyList[Ticker],
      interval: Interval,
      range: Range,
      parallelism: Int = YFinanceClient.DefaultParallelism
  )(implicit C: Concurrent[F]): F[Map[Ticker, ChartResult]] =
    YFinanceClient.downloadMulti(tickers, parallelism) { ticker =>
      charts.getChart(ticker, interval, range).flatMap {
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
      charts.getChart(ticker, interval, since, until).flatMap {
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
      charts.getStock(ticker).flatMap {
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
      financials.getFinancialStatements(ticker, frequency).flatMap {
        case Some(result) => C.pure(result)
        case None =>
          C.raiseError[FinancialStatements](new NoSuchElementException(s"No financial data for ${ticker.value}"))
      }
    }
}

object YFinanceClient {

  private[yfinance4s] val DefaultParallelism = 4

  private[yfinance4s] object SearchDefaults {
    val MaxResults = 8
    val NewsCount = 8
    val EnableFuzzyQuery = false
  }

  def resource[F[_]: Async](config: YFinanceClientConfig): Resource[F, YFinanceClient[F]] =
    for {
      gateway <- YFinanceGateway.resource[F](config.connectTimeout, config.readTimeout, config.retries)
      scrapper <- YFinanceScrapper.resource[F](config.connectTimeout, config.readTimeout, config.retries)
      auth <- YFinanceAuth.resource[F](config.connectTimeout, config.readTimeout, config.retries)
    } yield new Impl(gateway, scrapper, auth)

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

    val charts: Charts[F] = new Charts.Impl(gateway, scrapper)
    val options: Options[F] = new Options.Impl(gateway, auth)
    val holders: Holders[F] = new Holders.Impl(gateway, auth)
    val financials: Financials[F] = new Financials.Impl(gateway)
    val analysts: Analysts[F] = new Analysts.Impl(gateway, auth)

    def search(
        query: String,
        maxResults: Int,
        newsCount: Int,
        enableFuzzyQuery: Boolean
    ): F[SearchResult] =
      gateway.search(query, maxResults, newsCount, enableFuzzyQuery).map(mapSearchResult)

    // --- Search Mapping Helpers ---

    private def mapSearchResult(result: YFinanceSearchResult): SearchResult =
      SearchResult(
        quotes = result.quotes.flatMap(mapSearchQuote),
        news = result.news.map(mapSearchNews),
        lists = result.lists.getOrElse(List.empty).flatMap(mapSearchList),
        totalResults = result.count
      )

    private def mapSearchQuote(raw: SearchQuoteRaw): Option[QuoteSearchResult] =
      raw.symbol.map { sym =>
        QuoteSearchResult(
          symbol = sym,
          shortName = raw.shortname,
          longName = raw.longname,
          quoteType = raw.quoteType,
          exchange = raw.exchange,
          exchangeDisplay = raw.exchDisp,
          sector = raw.sectorDisp.orElse(raw.sector),
          industry = raw.industryDisp.orElse(raw.industry),
          score = raw.score
        )
      }

    private def mapSearchNews(raw: SearchNewsRaw): NewsItem = {
      val thumbnailUrl = raw.thumbnail
        .flatMap(_.resolutions.headOption)
        .map(_.url)

      NewsItem(
        uuid = raw.uuid,
        title = raw.title,
        publisher = raw.publisher,
        link = raw.link,
        publishTime = epochToZonedDateTime(raw.providerPublishTime),
        articleType = raw.`type`,
        thumbnailUrl = thumbnailUrl,
        relatedTickers = raw.relatedTickers.getOrElse(List.empty)
      )
    }

    private def mapSearchList(raw: SearchListRaw): Option[SearchList] =
      for {
        slug <- raw.slug
        title <- raw.title
      } yield SearchList(
        slug = slug,
        title = title,
        description = raw.description,
        canonicalName = raw.canonicalName
      )
  }
}
