package org.coinductive.yfinance4s

import org.coinductive.yfinance4s.models.internal.{ChartMetaRaw, InstrumentData}

import scala.concurrent.duration.*

/** Pure currency-standardisation core.
  *
  * Yahoo quotes some international markets in currency subunits (LSE in pence, Johannesburg in cents, Tel Aviv in
  * agora, Kuwait in fils). This converts such charts to their major unit and relabels them, so a cross-market caller
  * never mixes pence with pounds. It is a unit conversion, not an error repair: bars are never flagged `repaired`.
  */
private[yfinance4s] object CurrencyStandardisation {

  /** One subunit entry: the major-unit currency code and the subunit-to-major factor. */
  final case class Subunit(majorCurrency: String, factor: Double)

  /** Yahoo's subunit currency codes. GBp/ZAc/ILA mirror upstream's table; KWF is an extension (upstream leaves fils
    * unconverted). Open data - a new code is a one-line addition.
    */
  val SubunitCurrencies: Map[String, Subunit] = Map(
    "GBp" -> Subunit("GBP", 0.01), // UK pence -> pounds
    "ZAc" -> Subunit("ZAR", 0.01), // South African cents -> rand
    "ILA" -> Subunit("ILS", 0.01), // Israeli agora -> shekels
    "KWF" -> Subunit("KWD", 0.001) // Kuwaiti fils -> dinar (1000-subunit minor)
  )

  /** How fresh the last regular-market print must be, relative to the anchor bar, for the already-major cross-check to
    * be meaningful. Comparing a live price against a stale bar measures price drift, not units.
    */
  private val RecentTradeWindow: FiniteDuration = 30.days

  /** How close `regularMarketPrice / anchorClose * factor` must sit to 1 for the bars to count as already major. */
  private val AlreadyMajorTolerance = 0.1

  /** Average dividend yield above which unlabelled dividends must themselves be in the subunit - no real instrument
    * yields this much.
    */
  private val RidiculousYieldThreshold = 1.0

  /** The effective price currency after standardisation: the major-unit label when the pass would relabel this chart,
    * the raw label otherwise (standardisation off, non-subunit currency, or no bar has traded).
    */
  def targetCurrency(data: InstrumentData, standardise: Boolean): String = {
    val raw = data.meta.currency
    if (!standardise) raw
    else
      SubunitCurrencies.get(raw) match {
        case Some(subunit) if data.indicators.quote.head.volume.exists(_ > 0L) => subunit.majorCurrency
        case _                                                                 => raw
      }
  }

  /** Applies the full standardisation pass and returns the transformed bars with the effective currency label.
    *
    * Two sub-passes: dividends carrying an explicit subunit label are converted deterministically whatever the price
    * currency; then, when the price currency is itself a subunit code and some bar has traded, prices are rescaled
    * (unless the live quote shows they are already major), the chart is relabelled, and unlabelled dividends go through
    * the ridiculous-yield heuristic. Total - any decline leaves the affected half untouched.
    */
  def standardise(bars: Vector[PriceRepair.Bar], meta: ChartMetaRaw): (Vector[PriceRepair.Bar], String) = {
    val labelled = standardiseDividendLabels(bars)
    SubunitCurrencies.get(meta.currency) match {
      case None => (labelled, meta.currency)
      case Some(subunit) =>
        val anchorIdx = labelled.lastIndexWhere(_.volume > 0L)
        if (anchorIdx < 0) (labelled, meta.currency)
        else {
          val rescaled =
            if (alreadyMajor(labelled(anchorIdx), meta, subunit.factor)) labelled
            else labelled.map(rescalePrices(_, subunit.factor))
          (scaleRidiculousDividends(rescaled, subunit.factor), subunit.majorCurrency)
        }
    }
  }

  /** A dividend labelled in a subunit is in that subunit whatever the share price is quoted in, so it converts on its
    * label alone - the heuristic never sees it.
    */
  private def standardiseDividendLabels(bars: Vector[PriceRepair.Bar]): Vector[PriceRepair.Bar] =
    bars.map { b =>
      b.dividendCurrency.flatMap(SubunitCurrencies.get) match {
        case Some(subunit) =>
          b.copy(dividend = b.dividend * subunit.factor, dividendCurrency = Some(subunit.majorCurrency))
        case None => b
      }
    }

  /** True when the live quote is roughly `1 / factor` times the anchor close: the bars are already in major units and
    * must not be rescaled. Skipped whenever Yahoo omits either input or the print is stale relative to the anchor.
    */
  private def alreadyMajor(anchor: PriceRepair.Bar, meta: ChartMetaRaw, factor: Double): Boolean =
    (meta.regularMarketPrice, meta.regularMarketTime) match {
      case (Some(marketPrice), Some(marketTime)) =>
        val recent = math.abs(marketTime - anchor.datetime.toEpochSecond) <= RecentTradeWindow.toSeconds
        recent && math.abs(marketPrice / anchor.close * factor - 1.0) < AlreadyMajorTolerance
      case _ => false
    }

  private def rescalePrices(b: PriceRepair.Bar, factor: Double): PriceRepair.Bar =
    b.copy(
      open = b.open * factor,
      high = b.high * factor,
      low = b.low * factor,
      close = b.close * factor,
      adjClose = b.adjClose * factor
    )

  /** Scales unlabelled dividends when their average yield against the previous close is impossibly high - the sign that
    * Yahoo reported them in the subunit while the prices came in the major unit.
    */
  private def scaleRidiculousDividends(bars: Vector[PriceRepair.Bar], factor: Double): Vector[PriceRepair.Bar] = {
    val eligible = bars.indices.filter(i => bars(i).dividend != 0.0 && bars(i).dividendCurrency.isEmpty)
    if (eligible.isEmpty) bars
    else {
      val prevCloses = previousCloses(bars)
      val yields = eligible.map(i => bars(i).dividend / prevCloses(i))
      val average = yields.sum / yields.length
      if (average > RidiculousYieldThreshold)
        eligible.foldLeft(bars)((acc, i) => acc.updated(i, acc(i).copy(dividend = acc(i).dividend * factor)))
      else bars
    }
  }

  /** `prevClose(i)` is the close of bar `i - 1` carried forward across missing bars; the first bar uses its own close.
    * Only NaN closes are filled - a zero close is a real value here.
    */
  private def previousCloses(bars: Vector[PriceRepair.Bar]): Vector[Double] = {
    // scanLeft state after i bars = the last non-NaN close among them, so dropping the final state
    // yields exactly the previous-bar fill for every index.
    val carried = bars.scanLeft(Double.NaN)((last, b) => if (b.close.isNaN) last else b.close).init
    Vector.tabulate(bars.length)(i => if (i == 0) bars(0).close else carried(i))
  }
}
