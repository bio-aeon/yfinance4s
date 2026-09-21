package io.github.coinductive.yfinance4s

import cats.Applicative
import io.github.coinductive.yfinance4s.models.{Interval, Ticker}

/** Fills price/volume cells flagged as zero/NaN by fetching and aggregating finer-grained bars. The bars are passed
  * alongside the index-keyed tags so an implementation can resolve tagged rows to the date spans it must fetch. Only
  * [[IntervalReconstructor.noOp]] ships today; a gateway-backed implementation arrives with interval-reconstruction
  * support.
  */
private[yfinance4s] trait IntervalReconstructor[F[_]] {
  def reconstruct(
      ticker: Ticker,
      interval: Interval,
      bars: Vector[PriceRepair.Bar],
      tagged: PriceRepair.ZeroTags
  ): F[PriceRepair.Reconstruction]
}

private[yfinance4s] object IntervalReconstructor {

  /** Reconstructs nothing: every tagged cell is left to be restored to its original value. */
  def noOp[F[_]: Applicative]: IntervalReconstructor[F] =
    new IntervalReconstructor[F] {
      def reconstruct(
          ticker: Ticker,
          interval: Interval,
          bars: Vector[PriceRepair.Bar],
          tagged: PriceRepair.ZeroTags
      ): F[PriceRepair.Reconstruction] =
        Applicative[F].pure(PriceRepair.Reconstruction.empty)
    }
}
