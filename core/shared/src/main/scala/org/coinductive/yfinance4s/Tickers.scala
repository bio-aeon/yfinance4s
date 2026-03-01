package org.coinductive.yfinance4s

import cats.data.NonEmptyList
import cats.effect.Concurrent
import cats.effect.syntax.concurrent.*
import cats.syntax.applicativeError.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import org.coinductive.yfinance4s.models.*

import java.time.{LocalDate, ZonedDateTime}

/** A convenience wrapper for performing batch operations on multiple tickers.
  *
  * Provides a fluent, immutable API for fetching data across a set of ticker symbols in parallel. All data-fetching
  * methods delegate to the underlying [[YFinanceClient]] and execute requests concurrently with configurable
  * parallelism.
  *
  * Two execution modes are supported:
  *   - '''Fail-fast''' (default): Methods like `history`, `info`, etc. return `F[Map[Ticker, A]]` and raise an error if
  *     any single ticker fetch fails.
  *   - '''Error-tolerant''': Methods prefixed with `attempt` (e.g., `attemptHistory`) return `F[Map[Ticker,
  *     Either[Throwable, A]]]`, collecting all results including failures.
  *
  * @param client
  *   The underlying YFinanceClient that performs API calls
  * @param tickers
  *   Non-empty list of ticker symbols to operate on
  * @param parallelism
  *   Maximum number of concurrent requests (default: 4)
  */
final class Tickers[F[_]] private (
    val client: YFinanceClient[F],
    val tickers: NonEmptyList[Ticker],
    val parallelism: Int
) {

  // --- Pure Operations ---

  /** Returns a copy with updated parallelism. */
  def withParallelism(n: Int): Tickers[F] =
    new Tickers(client, tickers, n)

  /** Appends a ticker if not already present. */
  def add(ticker: Ticker): Tickers[F] =
    if (tickers.toList.contains(ticker)) this
    else new Tickers(client, tickers :+ ticker, parallelism)

  /** Appends multiple tickers, deduplicating against the current list. */
  def addAll(additional: NonEmptyList[Ticker]): Tickers[F] = {
    val existing = tickers.toList.toSet
    val newTickers = additional.toList.filterNot(existing.contains)
    newTickers match {
      case Nil => this
      case _   => new Tickers(client, NonEmptyList(tickers.head, tickers.tail ++ newTickers), parallelism)
    }
  }

  /** Removes a ticker. Returns None if the list would become empty. */
  def remove(ticker: Ticker): Option[Tickers[F]] = {
    val filtered = tickers.toList.filterNot(_ == ticker)
    NonEmptyList.fromList(filtered).map(nel => new Tickers(client, nel, parallelism))
  }

  /** Whether this set contains the given ticker. */
  def contains(ticker: Ticker): Boolean = tickers.toList.contains(ticker)

  /** Number of tickers. */
  def size: Int = tickers.size

  /** String representations of all tickers. */
  def symbols: NonEmptyList[String] = tickers.map(_.value)

  // --- Fail-Fast Data Methods ---

  /** Fetches historical chart data for all tickers by range. */
  def history(interval: Interval, range: Range)(implicit C: Concurrent[F]): F[Map[Ticker, ChartResult]] =
    fetchAll(_.getChart(_, interval, range), "chart data")

  /** Fetches historical chart data for all tickers by date range. */
  def history(interval: Interval, since: ZonedDateTime, until: ZonedDateTime)(implicit
      C: Concurrent[F]
  ): F[Map[Ticker, ChartResult]] =
    fetchAll(_.getChart(_, interval, since, until), "chart data")

  /** Fetches current stock quotes for all tickers. */
  def info(implicit C: Concurrent[F]): F[Map[Ticker, StockResult]] =
    fetchAll(_.getStock(_), "stock data")

  /** Fetches financial statements for all tickers. */
  def financials(frequency: Frequency = Frequency.Yearly)(implicit
      C: Concurrent[F]
  ): F[Map[Ticker, FinancialStatements]] =
    fetchAll(_.getFinancialStatements(_, frequency), "financial data")

  /** Fetches dividend history for all tickers. */
  def dividends(interval: Interval, range: Range)(implicit C: Concurrent[F]): F[Map[Ticker, List[DividendEvent]]] =
    fetchAll(_.getDividends(_, interval, range), "dividend data")

  /** Fetches stock split history for all tickers. */
  def splits(interval: Interval, range: Range)(implicit C: Concurrent[F]): F[Map[Ticker, List[SplitEvent]]] =
    fetchAll(_.getSplits(_, interval, range), "split data")

  /** Fetches combined corporate actions for all tickers. */
  def corporateActions(interval: Interval, range: Range)(implicit
      C: Concurrent[F]
  ): F[Map[Ticker, CorporateActions]] =
    fetchAll(_.getCorporateActions(_, interval, range), "corporate actions")

  /** Fetches holders information for all tickers. */
  def holdersData(implicit C: Concurrent[F]): F[Map[Ticker, HoldersData]] =
    fetchAll(_.getHoldersData(_), "holders data")

  /** Fetches analyst data for all tickers. */
  def analystData(implicit C: Concurrent[F]): F[Map[Ticker, AnalystData]] =
    fetchAll(_.getAnalystData(_), "analyst data")

  /** Fetches option expiration dates for all tickers. */
  def optionExpirations(implicit C: Concurrent[F]): F[Map[Ticker, List[LocalDate]]] =
    fetchAll(_.getOptionExpirations(_), "option expirations")

  // --- Error-Tolerant Data Methods ---

  /** Fetches historical chart data, collecting errors per ticker. */
  def attemptHistory(interval: Interval, range: Range)(implicit
      C: Concurrent[F]
  ): F[Map[Ticker, Either[Throwable, ChartResult]]] =
    fetchAllAttempt(_.getChart(_, interval, range), "chart data")

  /** Fetches historical chart data by date range, collecting errors per ticker. */
  def attemptHistory(interval: Interval, since: ZonedDateTime, until: ZonedDateTime)(implicit
      C: Concurrent[F]
  ): F[Map[Ticker, Either[Throwable, ChartResult]]] =
    fetchAllAttempt(_.getChart(_, interval, since, until), "chart data")

  /** Fetches current stock quotes, collecting errors per ticker. */
  def attemptInfo(implicit C: Concurrent[F]): F[Map[Ticker, Either[Throwable, StockResult]]] =
    fetchAllAttempt(_.getStock(_), "stock data")

  /** Fetches financial statements, collecting errors per ticker. */
  def attemptFinancials(frequency: Frequency = Frequency.Yearly)(implicit
      C: Concurrent[F]
  ): F[Map[Ticker, Either[Throwable, FinancialStatements]]] =
    fetchAllAttempt(_.getFinancialStatements(_, frequency), "financial data")

  /** Fetches dividend history, collecting errors per ticker. */
  def attemptDividends(interval: Interval, range: Range)(implicit
      C: Concurrent[F]
  ): F[Map[Ticker, Either[Throwable, List[DividendEvent]]]] =
    fetchAllAttempt(_.getDividends(_, interval, range), "dividend data")

  /** Fetches stock split history, collecting errors per ticker. */
  def attemptSplits(interval: Interval, range: Range)(implicit
      C: Concurrent[F]
  ): F[Map[Ticker, Either[Throwable, List[SplitEvent]]]] =
    fetchAllAttempt(_.getSplits(_, interval, range), "split data")

  /** Fetches corporate actions, collecting errors per ticker. */
  def attemptCorporateActions(interval: Interval, range: Range)(implicit
      C: Concurrent[F]
  ): F[Map[Ticker, Either[Throwable, CorporateActions]]] =
    fetchAllAttempt(_.getCorporateActions(_, interval, range), "corporate actions")

  /** Fetches holders information, collecting errors per ticker. */
  def attemptHoldersData(implicit C: Concurrent[F]): F[Map[Ticker, Either[Throwable, HoldersData]]] =
    fetchAllAttempt(_.getHoldersData(_), "holders data")

  /** Fetches analyst data, collecting errors per ticker. */
  def attemptAnalystData(implicit C: Concurrent[F]): F[Map[Ticker, Either[Throwable, AnalystData]]] =
    fetchAllAttempt(_.getAnalystData(_), "analyst data")

  /** Fetches option expiration dates, collecting errors per ticker. */
  def attemptOptionExpirations(implicit C: Concurrent[F]): F[Map[Ticker, Either[Throwable, List[LocalDate]]]] =
    fetchAllAttempt(_.getOptionExpirations(_), "option expirations")

  // --- Internal Helpers ---

  /** Fail-fast parallel fetch. Raises on the first failure. */
  private def fetchAll[A](
      f: (YFinanceClient[F], Ticker) => F[Option[A]],
      dataType: String
  )(implicit C: Concurrent[F]): F[Map[Ticker, A]] =
    tickers.toList
      .parTraverseN(parallelism) { ticker =>
        requireSome(ticker, dataType)(f(client, ticker)).map(ticker -> _)
      }
      .map(_.toMap)

  /** Error-tolerant parallel fetch. Collects all results as Either. */
  private def fetchAllAttempt[A](
      f: (YFinanceClient[F], Ticker) => F[Option[A]],
      dataType: String
  )(implicit C: Concurrent[F]): F[Map[Ticker, Either[Throwable, A]]] =
    tickers.toList
      .parTraverseN(parallelism) { ticker =>
        requireSome(ticker, dataType)(f(client, ticker)).attempt.map(ticker -> _)
      }
      .map(_.toMap)

  /** Unwraps F[Option[A]] into F[A], raising NoSuchElementException when None. */
  private def requireSome[A](ticker: Ticker, dataType: String)(fo: F[Option[A]])(implicit
      C: Concurrent[F]
  ): F[A] =
    fo.flatMap {
      case Some(a) => C.pure(a)
      case None    => C.raiseError(new NoSuchElementException(s"No $dataType for ${ticker.value}"))
    }
}

object Tickers {

  private val DefaultParallelism = 4

  /** Construct from a NonEmptyList of Tickers. */
  def apply[F[_]](
      client: YFinanceClient[F],
      tickers: NonEmptyList[Ticker],
      parallelism: Int = DefaultParallelism
  ): Tickers[F] = new Tickers(client, tickers, parallelism)

  /** Construct from varargs of string ticker symbols. Requires at least one symbol. */
  def of[F[_]](client: YFinanceClient[F], first: String, rest: String*): Tickers[F] = {
    val all = (first :: rest.toList).distinct.map(Ticker(_))
    new Tickers(client, NonEmptyList.fromListUnsafe(all), DefaultParallelism)
  }

  /** Construct for a single ticker. */
  def single[F[_]](client: YFinanceClient[F], ticker: Ticker): Tickers[F] =
    new Tickers(client, NonEmptyList.one(ticker), DefaultParallelism)
}
