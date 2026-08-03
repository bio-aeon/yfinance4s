package org.coinductive.yfinance4s

import cats.Functor
import cats.syntax.functor.*
import org.coinductive.yfinance4s.models.Ticker

/** Supplies the latest FX rate for a Yahoo currency-pair ticker. Kept as a seam so dividend FX conversion stays
  * testable without the sealed gateway.
  */
private[yfinance4s] trait FxRateSource[F[_]] {

  /** The newest usable close of the pair's daily chart, `None` when Yahoo has nothing usable. */
  def latestRate(ticker: Ticker): F[Option[Double]]
}

private[yfinance4s] object FxRateSource {

  /** Yahoo-backed source: a chart probe through the shared gateway (rate-limited and retried like every outbound
    * request), reduced by [[DividendFxConversion.lastUsableClose]].
    */
  def yahoo[F[_]: Functor](gateway: YFinanceGateway[F]): FxRateSource[F] =
    new FxRateSource[F] {
      def latestRate(ticker: Ticker): F[Option[Double]] =
        gateway
          .getChart(ticker, DividendFxConversion.FxProbeInterval, DividendFxConversion.FxProbeRange)
          .map(DividendFxConversion.lastUsableClose(ticker, _))
    }
}
