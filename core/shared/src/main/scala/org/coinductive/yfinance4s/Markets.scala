package org.coinductive.yfinance4s

import cats.Monad
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import org.coinductive.yfinance4s.Mapping.*
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.internal.*

/** Algebra for market-level data: region summaries, trading status, trending tickers. */
trait Markets[F[_]] {

  /** Headline indices and instruments for the given region (e.g., US → S&P 500, Dow, Nasdaq, VIX). */
  def getSummary(region: MarketRegion): F[MarketSummary]

  /** Trading status for the region: open/closed, session hours, timezone. */
  def getStatus(region: MarketRegion): F[MarketStatus]

  /** Most-searched tickers in the region. Yahoo caps the count server-side; the returned list may be shorter than
    * requested.
    */
  def getTrending(region: MarketRegion, count: Int = Markets.DefaultTrendingCount): F[List[TrendingTicker]]

  /** Convenience aggregate: fires all three queries sequentially and bundles the results. */
  def getMarketSnapshot(region: MarketRegion): F[MarketSnapshot]
}

private[yfinance4s] object Markets {

  val DefaultTrendingCount: Int = 10

  def apply[F[_]: Monad](gateway: YFinanceGateway[F], auth: YFinanceAuth[F]): Markets[F] =
    new MarketsImpl(gateway, auth)

  private final class MarketsImpl[F[_]: Monad](gateway: YFinanceGateway[F], auth: YFinanceAuth[F]) extends Markets[F] {

    def getSummary(region: MarketRegion): F[MarketSummary] =
      auth.getCredentials.flatMap { creds =>
        gateway.getMarketSummary(region, creds).map(mapMarketSummary(region, _))
      }

    def getStatus(region: MarketRegion): F[MarketStatus] =
      auth.getCredentials.flatMap { creds =>
        gateway.getMarketStatus(region, creds).map(mapMarketStatus(region, _))
      }

    def getTrending(region: MarketRegion, count: Int): F[List[TrendingTicker]] =
      auth.getCredentials.flatMap { creds =>
        gateway.getMarketTrending(region, count, creds).map(_.quotes.map(q => TrendingTicker(q.symbol)))
      }

    def getMarketSnapshot(region: MarketRegion): F[MarketSnapshot] =
      for {
        summary <- getSummary(region)
        status <- getStatus(region)
        trending <- getTrending(region)
      } yield MarketSnapshot(region, summary, status, trending)

    // --- Mapping helpers ---

    private def mapMarketSummary(region: MarketRegion, result: YFinanceMarketSummaryResult): MarketSummary =
      MarketSummary(
        region = region,
        quotes = result.result.map(mapMarketQuote)
      )

    private def mapMarketQuote(raw: MarketQuoteRaw): MarketIndexQuote =
      MarketIndexQuote(
        symbol = raw.symbol,
        shortName = raw.shortName,
        fullExchangeName = raw.fullExchangeName,
        exchange = raw.exchange,
        currency = raw.currency,
        marketState = raw.marketState,
        exchangeTimezoneName = raw.exchangeTimezoneName,
        regularMarketPrice = raw.regularMarketPrice.map(_.raw),
        regularMarketChange = raw.regularMarketChange.map(_.raw),
        regularMarketChangePercent = raw.regularMarketChangePercent.map(_.raw),
        regularMarketTime = raw.regularMarketTime.map(v => epochToZonedDateTime(v.raw)),
        previousClose = raw.previousClose.map(_.raw)
      )

    private def mapMarketStatus(region: MarketRegion, result: YFinanceMarketStatusResult): MarketStatus = {
      val raw = result.marketTime
      MarketStatus(
        region = region,
        status = raw.status,
        open = raw.open,
        close = raw.close,
        timezone = mapTimezone(raw.timezone),
        message = raw.message,
        yfitMarketState = raw.yfitMarketState
      )
    }

    private def mapTimezone(raw: TimezoneRaw): MarketTimezone =
      MarketTimezone(
        short = raw.short,
        gmtOffsetMillis = raw.gmtoffset,
        full = raw.full
      )
  }
}
