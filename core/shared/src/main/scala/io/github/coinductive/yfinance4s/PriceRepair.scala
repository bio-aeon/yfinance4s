package io.github.coinductive.yfinance4s

import io.github.coinductive.yfinance4s.models.*
import io.github.coinductive.yfinance4s.models.internal.{Chart, InstrumentData}

import java.time.{Instant, ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.*

/** Pure currency-unit price-repair core.
  *
  * Detects and repairs Yahoo's currency-subunit price errors: a systematic unit-switch detector (a whole block of bars
  * reported 100x/1000x off), a random-outlier detector (sporadic cents-for-dollars bars, rescaled directly since the
  * error magnitude is known exactly), and a zero/NaN detector for bars that should carry a trade. Everything here is
  * total and effect-free; the only effectful step (interval reconstruction) goes through [[IntervalReconstructor]].
  */
private[yfinance4s] object PriceRepair {

  private val SubunitFactorStandard = 100.0
  private val SubunitFactorThousand = 1000.0
  private val KuwaitiDinarSubunitCurrency = "KWF"
  private val FxTickerSuffix = "=X"

  private val RatioBucket = 20.0
  private val MixupRatio = 100.0
  private val SubunitScaleDown = 0.01
  private val SubunitScaleUp = 100.0

  private val EngineIgnoredRatioLow = 0.8
  private val EngineIgnoredRatioHigh = 1.25
  private val VolatilityGuardMultiplier = 5.0
  private val IqrOutlierMultiplier = 1.5
  private val LowerQuartilePercentile = 25.0
  private val UpperQuartilePercentile = 75.0
  private val LookbackDaily = 10
  private val LookbackMinutely = 100
  private val LookbackOther = 3
  private val LocalRecheckLookahead = 2
  private val SplitProximityWindow: FiniteDuration = 30.days

  private val IntradayBadDayFraction = 0.5
  private val InterdayCloseMoveThreshold = 0.05

  // Median-filter matrix layout: [High, Open, Low, Close, AdjClose] - order is load-bearing (High and Low
  // must not be direct neighbours in the 3x3 window).
  private val HighCol = 0
  private val OpenCol = 1
  private val LowCol = 2
  private val CloseCol = 3
  private val AdjCloseCol = 4
  private val PriceColumnCount = 5
  private val WindowSize = 9
  private val MedianIndex = 4
  private val OhlcColumnCount = 4

  private val IntervalDurations: Map[Interval, FiniteDuration] = Map(
    Interval.`1Minute` -> 1.minute,
    Interval.`2Minutes` -> 2.minutes,
    Interval.`5Minutes` -> 5.minutes,
    Interval.`15Minutes` -> 15.minutes,
    Interval.`30Minutes` -> 30.minutes,
    Interval.`60Minutes` -> 60.minutes,
    Interval.`90Minutes` -> 90.minutes,
    Interval.`1Hour` -> 1.hour,
    Interval.`1Day` -> 1.day
  )

  /** Intervals repair applies to: 1d plus the intraday intervals. `5d` and the multiday intervals pass through
    * untouched - Yahoo assembles multiday bars from potentially corrupt daily data, so they cannot be repaired in place
    * (the eventual path is to repair daily bars and resample).
    */
  val RepairableIntervals: Set[Interval] = IntervalDurations.keySet

  /** Context the pure algorithms need beyond the bars themselves. `currency` is deliberately the raw meta label, never
    * the standardised one: the unit-switch factor keys on Yahoo's subunit code (KWF means x1000) even after
    * standardisation relabels the chart to KWD.
    */
  final case class Context(currency: String, interval: Interval, isFx: Boolean)

  /** Index-addressable working row. Missing prices are `Double.NaN`; missing volume is `0`. `dividendCurrency` is
    * Yahoo's per-dividend label, rewritten in lockstep with `dividend` by whichever pass touches the amount.
    */
  final case class Bar(
      datetime: ZonedDateTime,
      open: Double,
      high: Double,
      low: Double,
      close: Double,
      adjClose: Double,
      volume: Long,
      dividend: Double,
      splitRatio: Double,
      repaired: Boolean,
      dividendCurrency: Option[String] = None
  )

  sealed trait PriceColumn
  object PriceColumn {
    case object Open extends PriceColumn
    case object High extends PriceColumn
    case object Low extends PriceColumn
    case object Close extends PriceColumn
    case object AdjClose extends PriceColumn
  }

  private val AllPriceColumns: Vector[PriceColumn] =
    Vector(PriceColumn.Open, PriceColumn.High, PriceColumn.Low, PriceColumn.Close, PriceColumn.AdjClose)

  /** Cells flagged by zero/NaN detection, addressed by row index and column. */
  final case class ZeroTags(priceCells: Set[(Int, PriceColumn)], volumeRows: Set[Int]) {
    def isEmpty: Boolean = priceCells.isEmpty && volumeRows.isEmpty
  }

  object ZeroTags {
    val empty: ZeroTags = ZeroTags(Set.empty, Set.empty)
  }

  /** Replacement values produced by an [[IntervalReconstructor]] for tagged cells (empty from the NoOp). */
  final case class Reconstruction(priceCells: Map[(Int, PriceColumn), Double], volumeRows: Map[Int, Long])

  object Reconstruction {
    val empty: Reconstruction = Reconstruction(Map.empty, Map.empty)
  }

  /** Everything the pure pipeline computed up to the effect boundary: the source data, the transformed bars, the
    * zero/NaN tags awaiting reconstruction (empty when nothing to do), and the effective price currency.
    */
  final case class Prepared(data: InstrumentData, bars: Vector[Bar], tags: ZeroTags, currency: String)

  /** Runs the pure half of the repair pipeline: currency standardisation and dividend FX application (every interval),
    * then the 100x family and zero/NaN detection (daily and intraday only). `fxRates` are the rates resolved for this
    * chart's mismatched dividend currencies, empty when conversion is off or nothing mismatched. Returns `None` iff the
    * chart carries no result data. A guarded-off chart yields untouched bars and empty tags, so `complete` reproduces
    * the unrepaired mapping.
    */
  def prepare(
      chart: Chart,
      ticker: Ticker,
      interval: Interval,
      cfg: PriceRepairConfig.Custom,
      fxRates: Map[String, Double] = Map.empty
  ): Option[Prepared] =
    chart.result.headOption.map { data =>
      val meta = data.meta
      val bars = toBars(data)
      val (standardised, label) =
        if (cfg.standardiseCurrency) CurrencyStandardisation.standardise(bars, meta)
        else (bars, meta.currency)
      val converted =
        if (cfg.convertDividendFx) DividendFxConversion.applyRates(standardised, label, fxRates)
        else standardised
      if (RepairableIntervals.contains(interval)) {
        val ctx = Context(meta.currency, interval, isFx = ticker.value.endsWith(FxTickerSuffix))
        val repaired = if (cfg.fix100xErrors) repairUnitMixups(converted, ctx) else converted
        val tags = if (cfg.fixZeroes) detectZeroes(repaired, ctx) else ZeroTags.empty
        Prepared(data, repaired, tags, label)
      } else Prepared(data, converted, ZeroTags.empty, label)
    }

  /** Applies the reconstruction (restoring originals for uncovered tags) and assembles the final [[ChartResult]]. The
    * dividends list is rebuilt from the repaired bars so a switch-scaled or converted dividend surfaces as such; splits
    * are re-attached from the raw events unchanged.
    */
  def complete(prepared: Prepared, recon: Reconstruction): ChartResult = {
    val bars = applyReconstruction(prepared.bars, prepared.tags, recon)
    val quotes = bars.map { b =>
      ChartResult.Quote(b.datetime, b.close, b.open, b.volume, b.high, b.low, b.adjClose, b.repaired)
    }.toList
    ChartResult(quotes, rebuiltDividends(prepared.data, bars), rawSplits(prepared.data), prepared.currency)
  }

  /** Dividend events with amount and currency taken from the (possibly transformed) bar at their timestamp; an event
    * with no matching bar keeps its raw amount and label.
    */
  private def rebuiltDividends(data: InstrumentData, bars: Vector[Bar]): List[DividendEvent] = {
    val dividendByEpoch: Map[Long, (Double, Option[String])] =
      data.timestamp.zip(bars).map { case (ts, b) => ts -> (b.dividend, b.dividendCurrency) }.toMap
    data.events
      .flatMap(_.dividends)
      .getOrElse(Map.empty)
      .map { case (key, raw) =>
        val event = DividendEvent.fromRaw(key, raw)
        dividendByEpoch.get(key.toLong).fold(event) { case (amount, currency) =>
          event.copy(amount = amount, currency = currency)
        }
      }
      .toList
      .sorted
  }

  private def rawSplits(data: InstrumentData): List[SplitEvent] =
    data.events
      .flatMap(_.splits)
      .getOrElse(Map.empty)
      .map { case (key, raw) => SplitEvent.fromRaw(key, raw) }
      .toList
      .sorted

  /** Converts decoded instrument data to working bars. Repair never adds, drops, or reorders rows. */
  def toBars(data: InstrumentData): Vector[Bar] = {
    val quote = data.indicators.quote.head
    val adjclose = data.indicators.adjclose.head
    val opens = quote.open.toVector
    val highs = quote.high.toVector
    val lows = quote.low.toVector
    val closes = quote.close.toVector
    val adjCloses = adjclose.adjclose.toVector
    val volumes = quote.volume.toVector

    val dividendAmounts: Map[Long, Double] =
      data.events.flatMap(_.dividends).getOrElse(Map.empty).map { case (key, raw) => key.toLong -> raw.amount }
    val dividendCurrencies: Map[Long, Option[String]] =
      data.events.flatMap(_.dividends).getOrElse(Map.empty).map { case (key, raw) =>
        key.toLong -> raw.normalisedCurrency
      }
    val splitFactors: Map[Long, Double] =
      data.events.flatMap(_.splits).getOrElse(Map.empty).map { case (key, raw) =>
        key.toLong -> raw.numerator.toDouble / raw.denominator.toDouble
      }

    data.timestamp.zipWithIndex.map { case (ts, i) =>
      Bar(
        datetime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneOffset.UTC),
        open = opens(i),
        high = highs(i),
        low = lows(i),
        close = closes(i),
        adjClose = adjCloses(i),
        volume = volumes(i),
        dividend = dividendAmounts.getOrElse(ts, 0.0),
        splitRatio = splitFactors.getOrElse(ts, 0.0),
        repaired = false,
        dividendCurrency = dividendCurrencies.getOrElse(ts, None)
      )
    }.toVector
  }

  /** The 100x family: systematic switch first (it moves whole blocks), then random outliers on the now-consistent
    * series.
    */
  def repairUnitMixups(bars: Vector[Bar], ctx: Context): Vector[Bar] =
    fixUnitRandomMixups(fixUnitSwitch(bars, ctx), ctx)

  /** Detects and rescales sporadic 100x/0.01x price outliers via a 3x3 wrap median filter. Rows containing a zero or
    * NaN price are excluded from detection and left to the zero path.
    */
  def fixUnitRandomMixups(bars: Vector[Bar], ctx: Context): Vector[Bar] =
    if (bars.length <= 1) bars
    else {
      val goodIdx = bars.indices.filterNot(i => hasZeroOrNaNPrice(bars(i))).toVector
      if (goodIdx.length <= 1) bars
      else {
        val matrix = goodIdx.map { i =>
          val b = bars(i)
          Array(b.high, b.open, b.low, b.close, b.adjClose)
        }
        val medians = medianFilter3x3Wrap(matrix)
        goodIdx.indices.foldLeft(bars) { (acc, r) =>
          mixupDirections(matrix(r), medians(r)) match {
            case None             => acc
            case Some(directions) => acc.updated(goodIdx(r), rescaledByDirections(acc(goodIdx(r)), directions))
          }
        }
      }
    }

  /** Per filter column: 1 = 100x too large, -1 = 100x too small, 0 = untouched; None when nothing is flagged. */
  private def mixupDirections(values: Array[Double], medians: Array[Double]): Option[Array[Int]] = {
    val directions = Array.tabulate(PriceColumnCount) { j =>
      val ratio = values(j) / medians(j)
      if (bucketedRatio(ratio) == MixupRatio) 1
      else if (bucketedRatio(1.0 / ratio) == MixupRatio) -1
      else 0
    }
    if (directions.forall(_ == 0)) None else Some(directions)
  }

  /** Rescales the flagged cells of one bar; a flagged High/Low is derived from the repaired Open/Close. */
  private def rescaledByDirections(b: Bar, directions: Array[Int]): Bar = {
    def rescaled(value: Double, direction: Int): Double =
      if (direction > 0) value * SubunitScaleDown
      else if (direction < 0) value * SubunitScaleUp
      else value
    val newOpen = rescaled(b.open, directions(OpenCol))
    val newClose = rescaled(b.close, directions(CloseCol))
    val newAdjClose = rescaled(b.adjClose, directions(AdjCloseCol))
    val newHigh = if (directions(HighCol) != 0) math.max(newOpen, newClose) else b.high
    val newLow = if (directions(LowCol) != 0) math.min(newOpen, newClose) else b.low
    b.copy(open = newOpen, high = newHigh, low = newLow, close = newClose, adjClose = newAdjClose, repaired = true)
  }

  /** Detects a systematic currency-unit switch (a whole block of bars 100x/1000x off) and rescales the affected block,
    * scaling coincident dividends with it. For a series that appears suspended at its newest end the repair is
    * conservatively skipped. Each stage declines by returning `None`, which leaves the bars untouched.
    */
  def fixUnitSwitch(bars: Vector[Bar], ctx: Context): Vector[Bar] = {
    val split =
      if (ctx.currency == KuwaitiDinarSubunitCurrency) SubunitFactorThousand else SubunitFactorStandard
    val splitMax = math.max(split, 1.0 / split)
    val repaired = for {
      rev <- newestFirstIfEligible(bars, split)
      changes = denoisedChangeRatios(rev)
      threshold <- detectionThreshold(changes, splitMax)
      transitions = survivingTransitions(changes, ctx, splitMax, threshold)
      if transitions.nonEmpty && !abortsNearSplit(rev, transitions, ctx, split)
    } yield rescaleBlocks(rev, transitionRanges(transitions, rev.length, split)).reverse
    repaired.getOrElse(bars)
  }

  /** A flagged switch boundary in newest-first indexing; `priceDropped` is the direction of the change at it. */
  private final case class Transition(index: Int, priceDropped: Boolean)

  /** Reverses to newest-first order when the switch repair may run at all: enough rows, a repairable change ratio (the
    * engine contract guard - unreachable for 100/1000, live once stock-split repair passes real split ratios), and no
    * suspension at the newest end.
    */
  private def newestFirstIfEligible(bars: Vector[Bar], split: Double): Option[Vector[Bar]] =
    if (bars.length <= 1 || (EngineIgnoredRatioLow < split && split < EngineIgnoredRatioHigh)) None
    else {
      val rev = bars.reverse // index 0 = newest bar; the algorithm walks the series newest-first
      val appearsSuspended = rev(0).volume == 0L || allOhlcMissing(rev(0))
      if (appearsSuspended) None else Some(rev)
    }

  /** OHLC adjusted by adjClose/close (a very large dividend must not masquerade as a switch); zero cells become 1.0. */
  private def adjustedOhlc(b: Bar): Array[Double] = {
    val adj = if (b.close == 0.0) 1.0 else b.adjClose / b.close
    Array(
      (if (b.open == 0.0) 1.0 else b.open) * adj,
      (if (b.high == 0.0) 1.0 else b.high) * adj,
      (if (b.low == 0.0) 1.0 else b.low) * adj,
      (if (b.close == 0.0) 1.0 else b.close) * adj
    )
  }

  /** Per-bar change ratios `x(i) = p(i) / p(i-1)` against the next-newer bar, median-denoised across the OHLC columns;
    * the newest row keeps 1.0, and ratio rows touching a zero Close are neutralised wholesale.
    */
  private def denoisedChangeRatios(rev: Vector[Bar]): Vector[Double] = {
    val p = rev.map(adjustedOhlc)
    def closeZero(i: Int): Boolean = rev(i).close == 0.0
    Vector.tabulate(rev.length) { i =>
      if (i == 0 || closeZero(i) || closeZero(i - 1)) 1.0
      else {
        val m = medianNumpy(Vector.tabulate(OhlcColumnCount)(j => p(i)(j) / p(i - 1)(j)))
        if (m.isNaN) 1.0 else m
      }
    }
  }

  /** The flagging threshold, or `None` when no change approaches the switch scale (early exit) or the switch would be
    * indistinguishable from normal volatility (IQR-filtered estimate).
    */
  private def detectionThreshold(changes: Vector[Double], splitMax: Double): Option[Double] = {
    val band = (splitMax - 1.0) * 0.5 + 1.0
    if (changes.max < band && changes.min > 1.0 / band) None
    else {
      val q1 = percentileLinear(changes, LowerQuartilePercentile)
      val q3 = percentileLinear(changes, UpperQuartilePercentile)
      val iqr = q3 - q1
      val kept =
        changes.filter(v => v >= q1 - IqrOutlierMultiplier * iqr && v <= q3 + IqrOutlierMultiplier * iqr)
      val avg = kept.sum / kept.length
      val largestChangePct = VolatilityGuardMultiplier * (populationStd(kept) / avg)
      if (splitMax < 1.0 + largestChangePct) None
      else Some(suddenChangeThreshold(splitMax, largestChangePct))
    }
  }

  /** Threshold-flagged transitions that survive the per-signal local recheck (jumps matching locally elevated
    * volatility are unflagged). Later windows see earlier unflags, so evaluation order matters.
    */
  private def survivingTransitions(
      changes: Vector[Double],
      ctx: Context,
      splitMax: Double,
      threshold: Double
  ): Vector[Transition] = {
    val n = changes.length
    val up = Array.tabulate(n)(i => changes(i) > threshold)
    val down = Array.tabulate(n)(i => changes(i) < 1.0 / threshold)
    val lookback =
      if (ctx.interval.value.endsWith("d")) LookbackDaily
      else if (ctx.interval.value.endsWith("m")) LookbackMinutely
      else LookbackOther
    (0 until n).filter(i => up(i) || down(i)).foreach { i =>
      val windowStart = math.max(0, i - lookback)
      val windowEnd = math.min(n - 1, i + LocalRecheckLookahead) // exclusive
      val clean = (windowStart until windowEnd).filterNot(k => up(k) || down(k)).map(changes)
      if (clean.nonEmpty) {
        val localAvg = clean.sum / clean.length
        if (localAvg > 0.0) {
          val localLargest = VolatilityGuardMultiplier * (populationStd(clean.toVector) / localAvg)
          val localThreshold = suddenChangeThreshold(splitMax, localLargest)
          if (changes(i) < localThreshold && changes(i) > 1.0 / localThreshold) {
            up(i) = false
            down(i) = false
          }
        }
      }
    }
    (0 until n).collect { case i if up(i) || down(i) => Transition(i, priceDropped = down(i)) }.toVector
  }

  /** Split-proximity abort: 100x factor only, one-sided (flags chronologically after a split), distance in bars x
    * interval duration - such changes are confusable with split errors.
    */
  private def abortsNearSplit(
      rev: Vector[Bar],
      transitions: Vector[Transition],
      ctx: Context,
      split: Double
  ): Boolean =
    split == SubunitFactorStandard && {
      val splitIdx = rev.indices.filter(i => rev(i).splitRatio != 0.0)
      val positiveGaps = for {
        s <- splitIdx
        t <- transitions
        gap = s - t.index
        if gap > 0
      } yield gap
      positiveGaps.nonEmpty &&
      IntervalDurations.get(ctx.interval).exists(_ * positiveGaps.min.toLong < SplitProximityWindow)
    }

  /** Pairs consecutive transitions into `[start, end)` rescale ranges; an odd tail runs to the oldest bar. The padded
    * newest row is never a genuine transition. split > 1, so a price drop at the boundary selects the up-scale factor.
    */
  private def transitionRanges(
      transitions: Vector[Transition],
      barCount: Int,
      split: Double
  ): Vector[(Int, Int, Double)] = {
    def factorFor(t: Transition): Double = if (t.priceDropped) split else 1.0 / split
    transitions.filterNot(_.index == 0).grouped(2).toVector.map {
      case Vector(start, end) => (start.index, end.index, factorFor(start))
      case group              => (group.head.index, barCount, factorFor(group.head))
    }
  }

  /** Multiplies every price (and coincident dividend) in each range by its factor, marking the bars repaired. */
  private def rescaleBlocks(rev: Vector[Bar], ranges: Vector[(Int, Int, Double)]): Vector[Bar] = {
    val out = rev.toArray
    ranges.foreach { case (start, end, factor) =>
      var i = start
      while (i < end) {
        val b = out(i)
        out(i) = b.copy(
          open = b.open * factor,
          high = b.high * factor,
          low = b.low * factor,
          close = b.close * factor,
          adjClose = b.adjClose * factor,
          dividend = b.dividend * factor,
          repaired = true
        )
        i += 1
      }
    }
    out.toVector
  }

  /** Detects zero/NaN prices (and missing volume) that should carry a trade. Returns index-keyed tags; bars are not
    * mutated.
    */
  def detectZeroes(bars: Vector[Bar], ctx: Context): ZeroTags = {
    val intraday = ctx.interval.value.endsWith("m") || ctx.interval.value.endsWith("h")
    val working = bars.indices.filterNot(intradayExcludedRows(bars, intraday)).toVector
    if (working.isEmpty) ZeroTags.empty
    else {
      val priceBad = badPriceCells(bars, working)
      val volBadInference = inferredBadVolumeRows(bars, working, ctx.isFx, intraday)

      // Repair-worth guards: nothing bad, or no calibration data at all.
      val badPriceCellCount = priceBad.valuesIterator.map(_.size).sum
      if (priceBad.isEmpty && volBadInference.isEmpty) ZeroTags.empty
      else if (badPriceCellCount == working.size * AllPriceColumns.size) ZeroTags.empty
      else {
        // Rule (c): volume missing on a bad-price row; rule (d): volume missing where price moved - the
        // latter applies to FX too.
        val volOnBadPriceRow = working.filter(i => priceBad.contains(i) && volumeMissing(bars(i)))
        val volOnMovedRow = working.filter(i => barMoved(bars(i)) && volumeMissing(bars(i)))
        val priceCells = priceBad.toSet[(Int, Set[PriceColumn])].flatMap { case (i, cols) =>
          cols.map(c => (i, c))
        }
        ZeroTags(priceCells, volBadInference ++ volOnBadPriceRow ++ volOnMovedRow)
      }
    }
  }

  /** Intraday day-exclusion: a day with >50% bad bars is a genuine low-liquidity session, not an error. Grouped by UTC
    * date until reconstruction threads the exchange offset.
    */
  private def intradayExcludedRows(bars: Vector[Bar], intraday: Boolean): Set[Int] =
    if (!intraday) Set.empty
    else
      bars.indices
        .groupBy(i => bars(i).datetime.toLocalDate)
        .collect {
          case (_, idxs)
              if idxs.count(i => zeroOrNaNColumns(bars(i)).nonEmpty).toDouble / idxs.size >
                IntradayBadDayFraction =>
            idxs
        }
        .flatten
        .toSet

  /** Zero/NaN price cells per row, plus split-implies-trading: a split day with no movement must have traded, so its
    * whole row is forced bad.
    */
  private def badPriceCells(bars: Vector[Bar], working: Vector[Int]): Map[Int, Set[PriceColumn]] =
    working.flatMap { i =>
      val b = bars(i)
      val splitWithoutMovement = b.splitRatio != 0.0 && !barMoved(b)
      val cols = if (splitWithoutMovement) AllPriceColumns.toSet else zeroOrNaNColumns(b)
      if (cols.nonEmpty) Some(i -> cols) else None
    }.toMap

  /** Rules (a) + (b): guard-participating bad-volume inference, non-FX only (FX volume is always zero) - intra-bar
    * movement without volume, and an interday close move above the threshold without volume (first row exempt).
    */
  private def inferredBadVolumeRows(
      bars: Vector[Bar],
      working: Vector[Int],
      isFx: Boolean,
      intraday: Boolean
  ): Set[Int] =
    if (isFx) Set.empty
    else {
      val intraBarMovement = working.filter { i =>
        volumeMissing(bars(i)) && !bars(i).high.isNaN && !bars(i).low.isNaN && barMoved(bars(i))
      }
      val interdayCloseMove =
        if (intraday) Vector.empty[Int]
        else
          working.filter { i =>
            volumeMissing(bars(i)) && i > 0 &&
            math.abs(bars(i).close - bars(i - 1).close) / bars(i).close > InterdayCloseMoveThreshold
          }
      (intraBarMovement ++ interdayCloseMove).toSet
    }

  /** Writes reconstructed values for covered tags (marking those bars repaired) and restores originals for the rest
    * (leaving their `repaired` flags untouched - a restore never erases another pass's provenance).
    */
  def applyReconstruction(bars: Vector[Bar], tags: ZeroTags, recon: Reconstruction): Vector[Bar] = {
    val afterPrices = tags.priceCells.groupBy(_._1).foldLeft(bars) { case (acc, (i, cells)) =>
      val (bar, changed) = cells.foldLeft((acc(i), false)) { case ((b, alreadyChanged), (_, col)) =>
        recon.priceCells.get((i, col)) match {
          case Some(value) => (withPrice(b, col, value), true)
          case None        => (b, alreadyChanged)
        }
      }
      if (changed) acc.updated(i, bar.copy(repaired = true)) else acc
    }
    tags.volumeRows.foldLeft(afterPrices) { (acc, i) =>
      recon.volumeRows.get(i) match {
        case Some(volume) => acc.updated(i, acc(i).copy(volume = volume, repaired = true))
        case None         => acc
      }
    }
  }

  /** 3x3 median filter with periodic ("wrap") boundaries on both axes, matching
    * `scipy.ndimage.median_filter(size=(3,3), mode="wrap")`.
    */
  def medianFilter3x3Wrap(matrix: Vector[Array[Double]]): Vector[Array[Double]] = {
    val rows = matrix.length
    Vector.tabulate(rows) { i =>
      Array.tabulate(PriceColumnCount) { j =>
        val window = new Array[Double](WindowSize)
        var k = 0
        var di = -1
        while (di <= 1) {
          var dj = -1
          while (dj <= 1) {
            window(k) = matrix(Math.floorMod(i + di, rows))(Math.floorMod(j + dj, PriceColumnCount))
            k += 1
            dj += 1
          }
          di += 1
        }
        java.util.Arrays.sort(window)
        window(MedianIndex)
      }
    }
  }

  // --- Numeric helpers pinned to numpy semantics ---

  /** The outlier test's `round(ratio / 20) * 20` with numpy's round-half-to-even. */
  def bucketedRatio(ratio: Double): Double = math.rint(ratio / RatioBucket) * RatioBucket

  /** numpy median: middle element for odd counts, mean of the two middle elements for even counts, NaN if any element
    * is NaN.
    */
  def medianNumpy(values: Vector[Double]): Double =
    if (values.exists(_.isNaN)) Double.NaN
    else {
      val sorted = values.sorted
      val n = sorted.length
      if (n % 2 == 1) sorted(n / 2)
      else (sorted(n / 2 - 1) + sorted(n / 2)) / 2.0
    }

  /** numpy percentile with linear interpolation between closest ranks. */
  def percentileLinear(values: Vector[Double], percentile: Double): Double = {
    val sorted = values.sorted
    val rank = percentile / 100.0 * (sorted.length - 1)
    val lower = math.floor(rank).toInt
    val upper = math.min(lower + 1, sorted.length - 1)
    val fraction = rank - lower
    sorted(lower) * (1.0 - fraction) + sorted(upper) * fraction
  }

  /** numpy std with `ddof = 0` (population standard deviation). */
  def populationStd(values: Vector[Double]): Double = {
    val mean = values.sum / values.length
    math.sqrt(values.map(v => (v - mean) * (v - mean)).sum / values.length)
  }

  // --- Private helpers ---

  /** Halfway between the split ratio and the largest expected normal change. */
  private def suddenChangeThreshold(splitMax: Double, largestChangePct: Double): Double =
    (splitMax + 1.0 + largestChangePct) * 0.5

  private def isZeroOrNaN(value: Double): Boolean = value == 0.0 || value.isNaN

  private def hasZeroOrNaNPrice(b: Bar): Boolean =
    isZeroOrNaN(b.open) || isZeroOrNaN(b.high) || isZeroOrNaN(b.low) || isZeroOrNaN(b.close) ||
      isZeroOrNaN(b.adjClose)

  private def zeroOrNaNColumns(b: Bar): Set[PriceColumn] =
    AllPriceColumns.filter(c => isZeroOrNaN(priceAt(b, c))).toSet

  private def barMoved(b: Bar): Boolean = b.high != b.low

  private def volumeMissing(b: Bar): Boolean = b.volume == 0L

  private def allOhlcMissing(b: Bar): Boolean =
    b.open.isNaN && b.high.isNaN && b.low.isNaN && b.close.isNaN

  private def priceAt(b: Bar, col: PriceColumn): Double = col match {
    case PriceColumn.Open     => b.open
    case PriceColumn.High     => b.high
    case PriceColumn.Low      => b.low
    case PriceColumn.Close    => b.close
    case PriceColumn.AdjClose => b.adjClose
  }

  private def withPrice(b: Bar, col: PriceColumn, value: Double): Bar = col match {
    case PriceColumn.Open     => b.copy(open = value)
    case PriceColumn.High     => b.copy(high = value)
    case PriceColumn.Low      => b.copy(low = value)
    case PriceColumn.Close    => b.copy(close = value)
    case PriceColumn.AdjClose => b.copy(adjClose = value)
  }
}
