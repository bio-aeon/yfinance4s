package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.*

import java.time.LocalDate

class AnalystDataSpec extends FunSuite {

  // --- AnalystPriceTargets tests ---

  private val priceTargets = AnalystPriceTargets(
    currentPrice = 200.0,
    targetHigh = 250.0,
    targetLow = 180.0,
    targetMean = 215.0,
    targetMedian = 220.0,
    numberOfAnalysts = 40,
    recommendationKey = "buy",
    recommendationMean = 1.8
  )

  test("calculates positive upside percent from current to mean target") {
    // (215 - 200) / 200 * 100 = 7.5%
    assert(Math.abs(priceTargets.meanUpsidePercent - 7.5) < 0.001)
  }

  test("calculates negative downside when current exceeds mean target") {
    val overpriced = priceTargets.copy(currentPrice = 230.0)
    // (215 - 230) / 230 * 100 = -6.521...
    assert(overpriced.meanUpsidePercent < 0)
  }

  test("returns zero upside when current price is zero") {
    val zeroPrice = priceTargets.copy(currentPrice = 0.0)
    assertEquals(zeroPrice.meanUpsidePercent, 0.0)
  }

  test("calculates median upside percent") {
    // (220 - 200) / 200 * 100 = 10.0%
    assert(Math.abs(priceTargets.medianUpsidePercent - 10.0) < 0.001)
  }

  test("calculates target range as high minus low") {
    // 250 - 180 = 70
    assert(Math.abs(priceTargets.targetRange - 70.0) < 0.001)
  }

  test("calculates target spread as percentage of mean") {
    // 70 / 215 * 100 = 32.558...
    assert(priceTargets.targetSpreadPercent > 32.0 && priceTargets.targetSpreadPercent < 33.0)
  }

  test("returns zero spread when mean target is zero") {
    val zeroMean = priceTargets.copy(targetMean = 0.0)
    assertEquals(zeroMean.targetSpreadPercent, 0.0)
  }

  test("detects price below all analyst targets") {
    val cheapStock = priceTargets.copy(currentPrice = 170.0)
    assert(cheapStock.isBelowAllTargets)
    assert(!priceTargets.isBelowAllTargets)
  }

  test("detects price above all analyst targets") {
    val expensiveStock = priceTargets.copy(currentPrice = 260.0)
    assert(expensiveStock.isAboveAllTargets)
    assert(!priceTargets.isAboveAllTargets)
  }

  // --- RecommendationTrend tests ---

  private val recTrend = RecommendationTrend(
    period = "0m",
    strongBuy = 15,
    buy = 25,
    hold = 8,
    sell = 1,
    strongSell = 0
  )

  test("sums all analyst categories") {
    assertEquals(recTrend.totalAnalysts, 49)
  }

  test("sums strong buy and buy into bullish count") {
    assertEquals(recTrend.totalBullish, 40)
  }

  test("sums sell and strong sell into bearish count") {
    assertEquals(recTrend.totalBearish, 1)
  }

  test("calculates bullish percent of total analysts") {
    // 40 / 49 * 100 = 81.63...
    assert(recTrend.bullishPercent > 81.0 && recTrend.bullishPercent < 82.0)
  }

  test("returns zero bearish percent when no analysts") {
    val empty = recTrend.copy(strongBuy = 0, buy = 0, hold = 0, sell = 0, strongSell = 0)
    assertEquals(empty.bearishPercent, 0.0)
  }

  test("net sentiment is positive when more bulls than bears") {
    assert(recTrend.netSentiment > 0)
    assertEquals(recTrend.netSentiment, 39) // 40 - 1
  }

  test("net sentiment is negative when more bears than bulls") {
    val bearish = recTrend.copy(strongBuy = 0, buy = 1, sell = 10, strongSell = 5)
    assert(bearish.netSentiment < 0)
  }

  test("is bullish when majority of analysts are bullish") {
    assert(recTrend.isBullish)
  }

  test("is not bullish when majority are neutral or bearish") {
    val neutral = recTrend.copy(strongBuy = 1, buy = 1, hold = 10, sell = 1, strongSell = 1)
    assert(!neutral.isBullish)
  }

  test("sorts recommendation trends with current month first") {
    val trends = List(
      recTrend.copy(period = "-2m"),
      recTrend.copy(period = "0m"),
      recTrend.copy(period = "-1m"),
      recTrend.copy(period = "-3m")
    )
    val sorted = trends.sorted
    assertEquals(sorted.map(_.period), List("0m", "-1m", "-2m", "-3m"))
  }

  // --- UpgradeDowngrade tests ---

  private val upgrade = UpgradeDowngrade(
    date = LocalDate.of(2024, 1, 15),
    firm = "Morgan Stanley",
    toGrade = "Overweight",
    fromGrade = Some("Equal-Weight"),
    action = UpgradeDowngradeAction.Upgrade
  )

  test("identifies upgrade actions") {
    assert(upgrade.isUpgrade)
    assert(!upgrade.isDowngrade)
    assert(!upgrade.isInitiation)
    assert(!upgrade.isMaintained)
  }

  test("identifies downgrade actions") {
    val downgrade = upgrade.copy(action = UpgradeDowngradeAction.Downgrade)
    assert(downgrade.isDowngrade)
    assert(!downgrade.isUpgrade)
  }

  test("identifies initiation actions") {
    val initiation = upgrade.copy(action = UpgradeDowngradeAction.Initiation, fromGrade = None)
    assert(initiation.isInitiation)
  }

  test("identifies maintained and reiterated actions") {
    val maintained = upgrade.copy(action = UpgradeDowngradeAction.Maintained)
    val reiterated = upgrade.copy(action = UpgradeDowngradeAction.Reiterated)
    assert(maintained.isMaintained)
    assert(reiterated.isMaintained)
  }

  test("parses known upgrade/downgrade action strings") {
    assertEquals(UpgradeDowngradeAction.fromString("up"), UpgradeDowngradeAction.Upgrade)
    assertEquals(UpgradeDowngradeAction.fromString("down"), UpgradeDowngradeAction.Downgrade)
    assertEquals(UpgradeDowngradeAction.fromString("main"), UpgradeDowngradeAction.Maintained)
    assertEquals(UpgradeDowngradeAction.fromString("reit"), UpgradeDowngradeAction.Reiterated)
    assertEquals(UpgradeDowngradeAction.fromString("init"), UpgradeDowngradeAction.Initiation)
  }

  test("wraps unknown action strings in Other") {
    val other = UpgradeDowngradeAction.fromString("suspend")
    assertEquals(other, UpgradeDowngradeAction.Other("suspend"))
    assertEquals(other.value, "suspend")
  }

  test("sorts upgrade/downgrade events most recent first") {
    val events = List(
      upgrade.copy(date = LocalDate.of(2023, 6, 1)),
      upgrade.copy(date = LocalDate.of(2024, 3, 15)),
      upgrade.copy(date = LocalDate.of(2024, 1, 10))
    )
    val sorted = events.sorted
    assertEquals(
      sorted.map(_.date),
      List(
        LocalDate.of(2024, 3, 15),
        LocalDate.of(2024, 1, 10),
        LocalDate.of(2023, 6, 1)
      )
    )
  }

  // --- EarningsEstimate tests ---

  private val earningsEst = EarningsEstimate(
    period = "0q",
    endDate = Some("2024-06-30"),
    avg = Some(2.04),
    low = Some(1.67),
    high = Some(2.47),
    yearAgoEps = Some(1.82),
    numberOfAnalysts = Some(31),
    growth = Some(0.12)
  )

  test("calculates earnings estimate range as high minus low") {
    val range = earningsEst.estimateRange
    assert(range.isDefined)
    assert(Math.abs(range.get - 0.80) < 0.001)
  }

  test("calculates earnings estimate spread as percentage of average") {
    val spread = earningsEst.estimateSpreadPercent
    assert(spread.isDefined)
    // 0.80 / 2.04 * 100 = 39.21...
    assert(spread.get > 39.0 && spread.get < 40.0)
  }

  test("returns no estimate spread when average is zero") {
    val zeroAvg = earningsEst.copy(avg = Some(0.0))
    assertEquals(zeroAvg.estimateSpreadPercent, None)
  }

  test("identifies quarterly earnings estimate periods") {
    assert(earningsEst.isQuarterly)
    assert(!earningsEst.isYearly)
  }

  test("identifies yearly earnings estimate periods") {
    val yearly = earningsEst.copy(period = "0y")
    assert(yearly.isYearly)
    assert(!yearly.isQuarterly)
  }

  test("returns no earnings estimate range when data is absent") {
    val noHigh = earningsEst.copy(high = None)
    assertEquals(noHigh.estimateRange, None)
  }

  // --- RevenueEstimate tests ---

  private val revenueEst = RevenueEstimate(
    period = "0q",
    endDate = Some("2024-06-30"),
    avg = Some(52247700000L),
    low = Some(48955000000L),
    high = Some(55838000000L),
    numberOfAnalysts = Some(27),
    yearAgoRevenue = Some(53809000000L),
    growth = Some(-0.029)
  )

  test("calculates revenue estimate range as high minus low") {
    val range = revenueEst.estimateRange
    assert(range.isDefined)
    assertEquals(range.get, 55838000000L - 48955000000L)
  }

  test("identifies quarterly revenue estimate periods") {
    assert(revenueEst.isQuarterly)
    assert(!revenueEst.isYearly)
  }

  test("identifies yearly revenue estimate periods") {
    val yearly = revenueEst.copy(period = "+1y")
    assert(yearly.isYearly)
    assert(!yearly.isQuarterly)
  }

  test("returns no revenue estimate range when data is absent") {
    val noData = revenueEst.copy(high = None, low = None)
    assertEquals(noData.estimateRange, None)
  }

  // --- EpsTrend tests ---

  private val epsTrend = EpsTrend(
    period = "0q",
    current = Some(2.04),
    sevenDaysAgo = Some(2.02),
    thirtyDaysAgo = Some(2.00),
    sixtyDaysAgo = Some(2.00),
    ninetyDaysAgo = Some(2.07)
  )

  test("calculates EPS change over seven days") {
    val change = epsTrend.changeSevenDays
    assert(change.isDefined)
    assert(Math.abs(change.get - 0.02) < 0.001)
  }

  test("calculates EPS change over thirty days") {
    val change = epsTrend.changeThirtyDays
    assert(change.isDefined)
    assert(Math.abs(change.get - 0.04) < 0.001)
  }

  test("calculates EPS change over ninety days") {
    val change = epsTrend.changeNinetyDays
    assert(change.isDefined)
    assert(Math.abs(change.get - (-0.03)) < 0.001)
  }

  test("trends up when 30-day EPS change is positive") {
    assertEquals(epsTrend.isTrendingUp, Some(true))
  }

  test("trends down when 30-day EPS change is negative") {
    val declining = epsTrend.copy(current = Some(1.95))
    assertEquals(declining.isTrendingUp, Some(false))
  }

  test("returns no EPS trend data when current estimate is absent") {
    val noData = epsTrend.copy(current = None)
    assertEquals(noData.changeSevenDays, None)
    assertEquals(noData.changeThirtyDays, None)
    assertEquals(noData.changeNinetyDays, None)
    assertEquals(noData.isTrendingUp, None)
  }

  // --- EpsRevisions tests ---

  private val epsRevisions = EpsRevisions(
    period = "0q",
    upLast7Days = Some(4),
    upLast30Days = Some(10),
    downLast30Days = Some(2),
    downLast90Days = Some(3)
  )

  test("calculates net EPS revisions as ups minus downs") {
    assertEquals(epsRevisions.netRevisions30Days, Some(8))
  }

  test("has positive revision trend when ups exceed downs") {
    assertEquals(epsRevisions.isPositiveTrend, Some(true))
  }

  test("has negative revision trend when downs exceed ups") {
    val negative = epsRevisions.copy(upLast30Days = Some(1), downLast30Days = Some(5))
    assertEquals(negative.isPositiveTrend, Some(false))
  }

  test("returns no revision data when values are absent") {
    val noData = epsRevisions.copy(upLast30Days = None)
    assertEquals(noData.netRevisions30Days, None)
    assertEquals(noData.isPositiveTrend, None)
  }

  // --- EarningsHistory tests ---

  private val earningsHistoryEntry = EarningsHistory(
    quarter = LocalDate.of(2024, 3, 31),
    period = "-1q",
    epsActual = Some(1.52),
    epsEstimate = Some(1.43),
    epsDifference = Some(0.09),
    surprisePercent = Some(6.3)
  )

  test("identifies earnings beat when EPS exceeds estimate") {
    assertEquals(earningsHistoryEntry.isBeat, Some(true))
    assertEquals(earningsHistoryEntry.isMiss, Some(false))
  }

  test("identifies earnings miss when EPS falls short") {
    val miss = earningsHistoryEntry.copy(epsDifference = Some(-0.05))
    assertEquals(miss.isMiss, Some(true))
    assertEquals(miss.isBeat, Some(false))
  }

  test("identifies met earnings when EPS matches estimate") {
    val met = earningsHistoryEntry.copy(epsDifference = Some(0.0))
    assertEquals(met.isMet, Some(true))
    assertEquals(met.isBeat, Some(false))
    assertEquals(met.isMiss, Some(false))
  }

  test("returns no beat/miss status when EPS difference is absent") {
    val noData = earningsHistoryEntry.copy(epsDifference = None)
    assertEquals(noData.isBeat, None)
    assertEquals(noData.isMiss, None)
    assertEquals(noData.isMet, None)
  }

  test("sorts earnings history most recent quarter first") {
    val entries = List(
      earningsHistoryEntry.copy(quarter = LocalDate.of(2023, 6, 30)),
      earningsHistoryEntry.copy(quarter = LocalDate.of(2024, 3, 31)),
      earningsHistoryEntry.copy(quarter = LocalDate.of(2023, 12, 31))
    )
    val sorted = entries.sorted
    assertEquals(
      sorted.map(_.quarter),
      List(
        LocalDate.of(2024, 3, 31),
        LocalDate.of(2023, 12, 31),
        LocalDate.of(2023, 6, 30)
      )
    )
  }

  // --- GrowthEstimates tests ---

  private val growthEst = GrowthEstimates(
    period = "0q",
    stockGrowth = Some(0.12),
    indexGrowth = Some(0.08),
    indexSymbol = Some("SP5")
  )

  test("outperforms when stock growth exceeds index") {
    assertEquals(growthEst.isOutperforming, Some(true))
  }

  test("underperforms when stock growth trails index") {
    val underperforming = growthEst.copy(stockGrowth = Some(0.05))
    assertEquals(underperforming.isOutperforming, Some(false))
  }

  test("calculates growth differential as stock minus index") {
    val diff = growthEst.growthDifferential
    assert(diff.isDefined)
    assert(Math.abs(diff.get - 0.04) < 0.001)
  }

  test("returns no performance comparison when growth data is absent") {
    val noStock = growthEst.copy(stockGrowth = None)
    assertEquals(noStock.isOutperforming, None)
    assertEquals(noStock.growthDifferential, None)

    val noIndex = growthEst.copy(indexGrowth = None)
    assertEquals(noIndex.isOutperforming, None)
    assertEquals(noIndex.growthDifferential, None)
  }

  // --- AnalystData tests ---

  test("empty instance is empty") {
    assert(AnalystData.empty.isEmpty)
  }

  test("is non-empty when price targets are present") {
    val withTargets = AnalystData.empty.copy(priceTargets = Some(priceTargets))
    assert(withTargets.nonEmpty)
  }

  test("is non-empty when recommendations are present") {
    val withRecs = AnalystData.empty.copy(recommendations = List(recTrend))
    assert(withRecs.nonEmpty)
  }

  test("is non-empty when earnings estimates are present") {
    val withEstimates = AnalystData.empty.copy(earningsEstimates = List(earningsEst))
    assert(withEstimates.nonEmpty)
  }

  test("is non-empty when upgrade/downgrade history is present") {
    val withHistory = AnalystData.empty.copy(upgradeDowngradeHistory = List(upgrade))
    assert(withHistory.nonEmpty)
  }

  test("is non-empty when earnings history is present") {
    val withEarnings = AnalystData.empty.copy(earningsHistory = List(earningsHistoryEntry))
    assert(withEarnings.nonEmpty)
  }

  test("is non-empty when growth estimates are present") {
    val withGrowth = AnalystData.empty.copy(growthEstimates = List(growthEst))
    assert(withGrowth.nonEmpty)
  }

  test("finds current month recommendation (0m)") {
    val data = AnalystData.empty.copy(recommendations =
      List(
        recTrend.copy(period = "-1m"),
        recTrend.copy(period = "0m"),
        recTrend.copy(period = "-2m")
      )
    )
    val current = data.currentRecommendation
    assert(current.isDefined)
    assertEquals(current.get.period, "0m")
  }

  test("returns no current recommendation when 0m period absent") {
    val data = AnalystData.empty.copy(recommendations =
      List(
        recTrend.copy(period = "-1m"),
        recTrend.copy(period = "-2m")
      )
    )
    assertEquals(data.currentRecommendation, None)
  }

  test("finds current quarter earnings estimate (0q)") {
    val data = AnalystData.empty.copy(earningsEstimates =
      List(
        earningsEst.copy(period = "+1q"),
        earningsEst.copy(period = "0q"),
        earningsEst.copy(period = "0y")
      )
    )
    val current = data.currentQuarterEarningsEstimate
    assert(current.isDefined)
    assertEquals(current.get.period, "0q")
  }

  test("finds current quarter revenue estimate (0q)") {
    val data = AnalystData.empty.copy(revenueEstimates =
      List(
        revenueEst.copy(period = "+1q"),
        revenueEst.copy(period = "0q")
      )
    )
    val current = data.currentQuarterRevenueEstimate
    assert(current.isDefined)
    assertEquals(current.get.period, "0q")
  }

  test("finds current year earnings estimate (0y)") {
    val data = AnalystData.empty.copy(earningsEstimates =
      List(
        earningsEst.copy(period = "0q"),
        earningsEst.copy(period = "0y"),
        earningsEst.copy(period = "+1y")
      )
    )
    val current = data.currentYearEarningsEstimate
    assert(current.isDefined)
    assertEquals(current.get.period, "0y")
  }

  test("counts consecutive earnings beats from most recent") {
    val history = List(
      EarningsHistory(LocalDate.of(2024, 3, 31), "-1q", Some(1.5), Some(1.4), Some(0.1), Some(7.0)),
      EarningsHistory(LocalDate.of(2023, 12, 31), "-2q", Some(1.3), Some(1.2), Some(0.1), Some(8.0)),
      EarningsHistory(LocalDate.of(2023, 9, 30), "-3q", Some(1.1), Some(1.15), Some(-0.05), Some(-4.3)),
      EarningsHistory(LocalDate.of(2023, 6, 30), "-4q", Some(1.0), Some(0.9), Some(0.1), Some(11.0))
    )
    val data = AnalystData.empty.copy(earningsHistory = history)
    // Sorted by most recent first: Q1-2024 (beat), Q4-2023 (beat), Q3-2023 (miss), Q2-2023 (beat)
    // Consecutive beats from front = 2
    assertEquals(data.consecutiveBeats, 2)
  }

  test("consecutive beats is zero when most recent is a miss") {
    val history = List(
      EarningsHistory(LocalDate.of(2024, 3, 31), "-1q", Some(1.3), Some(1.4), Some(-0.1), Some(-7.0)),
      EarningsHistory(LocalDate.of(2023, 12, 31), "-2q", Some(1.3), Some(1.2), Some(0.1), Some(8.0))
    )
    val data = AnalystData.empty.copy(earningsHistory = history)
    assertEquals(data.consecutiveBeats, 0)
  }

  test("filters and limits recent upgrades") {
    val events = List(
      UpgradeDowngrade(LocalDate.of(2024, 3, 1), "A", "Buy", Some("Hold"), UpgradeDowngradeAction.Upgrade),
      UpgradeDowngrade(LocalDate.of(2024, 2, 1), "B", "Hold", Some("Buy"), UpgradeDowngradeAction.Downgrade),
      UpgradeDowngrade(LocalDate.of(2024, 1, 1), "C", "Overweight", Some("Neutral"), UpgradeDowngradeAction.Upgrade),
      UpgradeDowngrade(LocalDate.of(2023, 12, 1), "D", "Buy", None, UpgradeDowngradeAction.Initiation)
    )
    val data = AnalystData.empty.copy(upgradeDowngradeHistory = events)
    val upgrades = data.recentUpgrades(2)
    assertEquals(upgrades.size, 2)
    assert(upgrades.forall(_.isUpgrade))
    // Should be sorted most recent first
    assertEquals(upgrades.head.firm, "A")
    assertEquals(upgrades(1).firm, "C")
  }

  test("filters and limits recent downgrades") {
    val events = List(
      UpgradeDowngrade(LocalDate.of(2024, 3, 1), "A", "Buy", Some("Hold"), UpgradeDowngradeAction.Upgrade),
      UpgradeDowngrade(LocalDate.of(2024, 2, 1), "B", "Hold", Some("Buy"), UpgradeDowngradeAction.Downgrade),
      UpgradeDowngrade(LocalDate.of(2024, 1, 1), "C", "Sell", Some("Hold"), UpgradeDowngradeAction.Downgrade)
    )
    val data = AnalystData.empty.copy(upgradeDowngradeHistory = events)
    val downgrades = data.recentDowngrades()
    assertEquals(downgrades.size, 2)
    assert(downgrades.forall(_.isDowngrade))
    assertEquals(downgrades.head.firm, "B")
  }

  test("calculates earnings beat rate as percentage") {
    val history = List(
      EarningsHistory(LocalDate.of(2024, 3, 31), "-1q", Some(1.5), Some(1.4), Some(0.1), Some(7.0)),
      EarningsHistory(LocalDate.of(2023, 12, 31), "-2q", Some(1.3), Some(1.2), Some(0.1), Some(8.0)),
      EarningsHistory(LocalDate.of(2023, 9, 30), "-3q", Some(1.1), Some(1.15), Some(-0.05), Some(-4.3)),
      EarningsHistory(LocalDate.of(2023, 6, 30), "-4q", Some(1.0), Some(0.9), Some(0.1), Some(11.0))
    )
    val data = AnalystData.empty.copy(earningsHistory = history)
    val rate = data.earningsBeatRate
    assert(rate.isDefined)
    // 3 beats out of 4 = 75%
    assert(Math.abs(rate.get - 75.0) < 0.001)
  }

  test("returns no beat rate for empty history") {
    assertEquals(AnalystData.empty.earningsBeatRate, None)
  }

  test("returns no beat rate when all EPS differences are absent") {
    val history = List(
      EarningsHistory(LocalDate.of(2024, 3, 31), "-1q", None, None, None, None)
    )
    val data = AnalystData.empty.copy(earningsHistory = history)
    assertEquals(data.earningsBeatRate, None)
  }

  test("empty instance returns defaults for all accessors") {
    val data = AnalystData.empty
    assertEquals(data.currentRecommendation, None)
    assertEquals(data.currentQuarterEarningsEstimate, None)
    assertEquals(data.currentQuarterRevenueEstimate, None)
    assertEquals(data.currentYearEarningsEstimate, None)
    assertEquals(data.consecutiveBeats, 0)
    assertEquals(data.recentUpgrades().size, 0)
    assertEquals(data.recentDowngrades().size, 0)
    assertEquals(data.earningsBeatRate, None)
  }
}
