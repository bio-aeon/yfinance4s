package org.coinductive.yfinance4s.models

/** Controls automatic detection and repair of currency-unit price errors in
  * [[org.coinductive.yfinance4s.Charts.getChart]].
  *
  * Yahoo Finance intermittently reports price bars in the wrong currency subunit (cents for dollars, pence for pounds),
  * i.e. exactly 100x off (or 1000x for currencies with a 1000-unit minor). Repair detects and corrects these. It is
  * best-effort and never fails a request: values that cannot be repaired confidently are returned unchanged.
  *
  * It also standardises subunit-quoted international charts (pence, cents, agora, fils) to their major currency unit
  * and converts dividends reported in a different currency than the share price. The two families differ in reach:
  * error repairs apply to daily and intraday intervals only (other intervals are returned unrepaired), while currency
  * standardisation and dividend FX conversion apply to every interval.
  *
  * Repaired bars carry `repaired = true` on [[ChartResult.Quote]], and a repaired chart's dividends reflect any
  * correction - [[org.coinductive.yfinance4s.Charts.getDividends]] always reports Yahoo's raw amounts and can therefore
  * disagree. [[ChartResult.currency]] reports the effective price currency, standardised or not.
  */
sealed trait PriceRepairConfig

object PriceRepairConfig {

  /** No repair. `getChart` returns Yahoo's bars verbatim. This is the default. */
  case object Disabled extends PriceRepairConfig

  /** Every repair the library can currently perform. Equivalent to `Custom(fix100xErrors = true, fixZeroes = true,
    * standardiseCurrency = true, convertDividendFx = true)`; widens automatically as future phases add repairs.
    */
  case object Enabled extends PriceRepairConfig

  /** Selective repair.
    *
    * @param fix100xErrors
    *   Detect and correct currency-subunit mixups: sporadic 100x/0.01x outliers and systematic unit switches. Gates
    *   both the random-outlier and the sudden-switch repairs.
    * @param fixZeroes
    *   Detect zero/NaN prices that should carry a trade and reconstruct them from finer-grained bars. Detection is
    *   active now; reconstruction activates when interval reconstruction is available - until then this flag is a safe
    *   no-op.
    * @param standardiseCurrency
    *   Convert subunit-quoted charts to their major currency unit (pence to pounds, cents to rand, agora to shekels,
    *   fils to dinar) and relabel [[ChartResult.currency]] accordingly. Dividends reported in the subunit are converted
    *   too. Applies to every interval. A unit conversion, not an error repair: bars keep `repaired = false`.
    * @param convertDividendFx
    *   Convert dividends whose reported currency differs from the price currency, using the latest Yahoo FX rate (one
    *   small chart fetch per distinct dividend currency, rate-limited like all requests; none for the common case of no
    *   mismatches). Best-effort: a dividend whose rate cannot be resolved keeps its original amount and
    *   [[DividendEvent.currency]] label.
    */
  final case class Custom(
      fix100xErrors: Boolean = true,
      fixZeroes: Boolean = true,
      standardiseCurrency: Boolean = true,
      convertDividendFx: Boolean = true
  ) extends PriceRepairConfig

  private[yfinance4s] def resolve(config: PriceRepairConfig): Custom =
    config match {
      case Disabled =>
        Custom(fix100xErrors = false, fixZeroes = false, standardiseCurrency = false, convertDividendFx = false)
      case Enabled =>
        Custom(fix100xErrors = true, fixZeroes = true, standardiseCurrency = true, convertDividendFx = true)
      case c: Custom => c
    }
}
