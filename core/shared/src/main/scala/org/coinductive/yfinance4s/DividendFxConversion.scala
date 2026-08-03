package org.coinductive.yfinance4s

import cats.MonadThrow
import cats.syntax.applicativeError.*
import cats.syntax.functor.*
import cats.syntax.traverse.*
import org.coinductive.yfinance4s.models.{Interval, PriceRepairConfig, Range, Ticker}
import org.coinductive.yfinance4s.models.internal.{Chart, InstrumentData}

/** Dividend FX conversion: planning and application are pure, only the rate lookup is effectful (through
  * [[FxRateSource]]).
  *
  * Some issuers pay dividends in a different currency than their share price. Yahoo labels those dividends with their
  * own currency; this converts the amounts into the price currency using the latest rate of the relevant Yahoo pair,
  * chaining through USD when no direct pair is likely to exist.
  */
private[yfinance4s] object DividendFxConversion {

  /** One FX chart to fetch: the Yahoo pair symbol and whether the fetched rate applies inverted. */
  final case class FxLeg(ticker: Ticker, inverted: Boolean)

  /** The conversion route for one dividend currency into the price currency (one or two legs). */
  final case class FxPlan(dividendCurrency: String, legs: List[FxLeg])

  /** The probe a rate lookup reads: one month of daily bars, the newest usable close of which is the rate. */
  val FxProbeInterval: Interval = Interval.`1Day`
  val FxProbeRange: Range = Range.`1Month`

  /** Currencies Yahoo reliably quotes against each other directly. */
  private val MajorCurrencies = Set("USD", "JPY", "EUR", "CNY", "GBP", "CAD")

  private val UsDollar = "USD"
  private val FxSuffix = "=X"

  /** Yahoo's `{CUR}=X` symbols quote USD against the currency. */
  private def usdPair(currency: String): Ticker = Ticker(s"$currency$FxSuffix")

  private def crossPair(from: String, to: String): Ticker = Ticker(s"$from$to$FxSuffix")

  /** The conversion routes a chart needs: one plan per distinct explicitly-labelled dividend currency differing from
    * the effective price currency. Empty when conversion is disabled, when nothing mismatches, or when the target is
    * itself a subunit code - so the common case costs no extra requests.
    *
    * A subunit-labelled dividend is planned under its major currency, because standardisation will have normalised its
    * amount by the time rates are applied; with standardisation off it is skipped entirely, since converting an
    * un-normalised subunit amount would be wrong by the subunit factor.
    */
  def requiredPlans(data: InstrumentData, cfg: PriceRepairConfig.Custom): List[FxPlan] =
    if (!cfg.convertDividendFx) Nil
    else {
      val target = CurrencyStandardisation.targetCurrency(data, cfg.standardiseCurrency)
      if (CurrencyStandardisation.SubunitCurrencies.contains(target)) Nil
      else
        data.events
          .flatMap(_.dividends)
          .getOrElse(Map.empty)
          .values
          .flatMap(_.normalisedCurrency)
          .toList
          .distinct
          .flatMap { label =>
            CurrencyStandardisation.SubunitCurrencies.get(label) match {
              case Some(subunit) => Option.when(cfg.standardiseCurrency)(subunit.majorCurrency)
              case None          => Some(label)
            }
          }
          .distinct
          .filterNot(_ == target)
          .map(currency => FxPlan(currency, legsFor(currency, target)))
    }

  private def legsFor(currency: String, target: String): List[FxLeg] =
    if (currency == UsDollar) List(FxLeg(usdPair(target), inverted = false))
    else if (target == UsDollar) List(FxLeg(usdPair(currency), inverted = true))
    else if (MajorCurrencies.contains(currency) && MajorCurrencies.contains(target))
      List(FxLeg(crossPair(currency, target), inverted = false))
    else List(FxLeg(usdPair(currency), inverted = true), FxLeg(usdPair(target), inverted = false))

  /** Resolves plans to multiplicative rates through the source, fetching each distinct leg ticker once. Best-effort: a
    * failed or unusable leg drops its currency from the result instead of failing the caller.
    */
  def resolveRates[F[_]: MonadThrow](plans: List[FxPlan], source: FxRateSource[F]): F[Map[String, Double]] = {
    val legTickers = plans.flatMap(_.legs.map(_.ticker)).distinct
    legTickers
      .traverse(ticker => source.latestRate(ticker).attempt.map(result => ticker -> result.toOption.flatten))
      .map { fetched =>
        val rateByTicker = fetched.toMap
        plans.flatMap { plan =>
          plan.legs
            .traverse { leg =>
              rateByTicker
                .getOrElse(leg.ticker, None)
                .filter(rate => !rate.isNaN && rate > 0.0)
                .map(rate => if (leg.inverted) 1.0 / rate else rate)
            }
            .map(factors => plan.dividendCurrency -> factors.product)
        }.toMap
      }
  }

  /** The newest usable close of a fetched FX chart: the random-outlier repair runs first - a corrupt final rate bar
    * would otherwise scale every converted dividend by a hundred - and then the newest finite, strictly positive close
    * wins.
    *
    * Only the random-outlier half of the 100x family is applied. The sudden-switch half treats the newest bar as its
    * baseline, so a lone corrupt newest bar reads as a block switch and rescales the good history up to match it -
    * leaving the corrupt rate in place. A systematic unit switch is not a real failure mode for a currency pair over a
    * one-month window; a sporadic bad bar is.
    */
  def lastUsableClose(fxTicker: Ticker, chart: Chart): Option[Double] =
    chart.result.headOption.flatMap { data =>
      val ctx = PriceRepair.Context(data.meta.currency, FxProbeInterval, isFx = true)
      PriceRepair
        .fixUnitRandomMixups(PriceRepair.toBars(data), ctx)
        .reverseIterator
        .map(_.close)
        .find(close => !close.isNaN && close > 0.0)
    }

  /** Converts labelled dividends whose currency has a resolved rate, relabelling them to the target currency;
    * everything else passes through untouched, so an unconverted dividend stays visibly foreign.
    */
  def applyRates(
      bars: Vector[PriceRepair.Bar],
      target: String,
      rates: Map[String, Double]
  ): Vector[PriceRepair.Bar] =
    if (rates.isEmpty) bars
    else
      bars.map { b =>
        b.dividendCurrency.filter(_ != target).flatMap(rates.get) match {
          case Some(rate) => b.copy(dividend = b.dividend * rate, dividendCurrency = Some(target))
          case None       => b
        }
      }
}
