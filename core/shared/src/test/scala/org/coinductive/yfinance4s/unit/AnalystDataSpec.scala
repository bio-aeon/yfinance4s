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

  test("meanUpsidePercent should calculate positive upside correctly") {
    // (215 - 200) / 200 * 100 = 7.5%
    assert(Math.abs(priceTargets.meanUpsidePercent - 7.5) < 0.001)
  }

  test("meanUpsidePercent should calculate negative downside correctly") {
    val overpriced = priceTargets.copy(currentPrice = 230.0)
    // (215 - 230) / 230 * 100 = -6.521...
    assert(overpriced.meanUpsidePercent < 0)
  }

  test("meanUpsidePercent should return 0 when current price is 0") {
    val zeroPrice = priceTargets.copy(currentPrice = 0.0)
    assertEquals(zeroPrice.meanUpsidePercent, 0.0)
  }

  test("medianUpsidePercent should calculate correctly") {
    // (220 - 200) / 200 * 100 = 10.0%
    assert(Math.abs(priceTargets.medianUpsidePercent - 10.0) < 0.001)
  }

  test("targetRange should calculate high minus low") {
    // 250 - 180 = 70
    assert(Math.abs(priceTargets.targetRange - 70.0) < 0.001)
  }

  test("targetSpreadPercent should calculate spread relative to mean") {
    // 70 / 215 * 100 = 32.558...
    assert(priceTargets.targetSpreadPercent > 32.0 && priceTargets.targetSpreadPercent < 33.0)
  }

  test("targetSpreadPercent should return 0 when mean is 0") {
    val zeroMean = priceTargets.copy(targetMean = 0.0)
    assertEquals(zeroMean.targetSpreadPercent, 0.0)
  }

  test("isBelowAllTargets should return true when price below lowest target") {
    val cheapStock = priceTargets.copy(currentPrice = 170.0)
    assert(cheapStock.isBelowAllTargets)
    assert(!priceTargets.isBelowAllTargets)
  }

  test("isAboveAllTargets should return true when price above highest target") {
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

  test("totalAnalysts should sum all categories") {
    assertEquals(recTrend.totalAnalysts, 49)
  }

  test("totalBullish should sum strongBuy and buy") {
    assertEquals(recTrend.totalBullish, 40)
  }

  test("totalBearish should sum sell and strongSell") {
    assertEquals(recTrend.totalBearish, 1)
  }

  test("bullishPercent should calculate correctly") {
    // 40 / 49 * 100 = 81.63...
    assert(recTrend.bullishPercent > 81.0 && recTrend.bullishPercent < 82.0)
  }

  test("bearishPercent should return 0 when no analysts") {
    val empty = recTrend.copy(strongBuy = 0, buy = 0, hold = 0, sell = 0, strongSell = 0)
    assertEquals(empty.bearishPercent, 0.0)
  }

  test("netSentiment should be positive when more bulls") {
    assert(recTrend.netSentiment > 0)
    assertEquals(recTrend.netSentiment, 39) // 40 - 1
  }

  test("netSentiment should be negative when more bears") {
    val bearish = recTrend.copy(strongBuy = 0, buy = 1, sell = 10, strongSell = 5)
    assert(bearish.netSentiment < 0)
  }

  test("isBullish should return true when bullish > 50%") {
    assert(recTrend.isBullish)
  }

  test("isBullish should return false when bullish <= 50%") {
    val neutral = recTrend.copy(strongBuy = 1, buy = 1, hold = 10, sell = 1, strongSell = 1)
    assert(!neutral.isBullish)
  }

  test("RecommendationTrend ordering should sort current month first") {
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

  test("isUpgrade should return true for upgrade actions") {
    assert(upgrade.isUpgrade)
    assert(!upgrade.isDowngrade)
    assert(!upgrade.isInitiation)
    assert(!upgrade.isMaintained)
  }

  test("isDowngrade should return true for downgrade actions") {
    val downgrade = upgrade.copy(action = UpgradeDowngradeAction.Downgrade)
    assert(downgrade.isDowngrade)
    assert(!downgrade.isUpgrade)
  }

  test("isInitiation should return true for init actions") {
    val initiation = upgrade.copy(action = UpgradeDowngradeAction.Initiation, fromGrade = None)
    assert(initiation.isInitiation)
  }

  test("isMaintained should return true for main and reit actions") {
    val maintained = upgrade.copy(action = UpgradeDowngradeAction.Maintained)
    val reiterated = upgrade.copy(action = UpgradeDowngradeAction.Reiterated)
    assert(maintained.isMaintained)
    assert(reiterated.isMaintained)
  }

  test("UpgradeDowngradeAction.fromString should handle known actions") {
    assertEquals(UpgradeDowngradeAction.fromString("up"), UpgradeDowngradeAction.Upgrade)
    assertEquals(UpgradeDowngradeAction.fromString("down"), UpgradeDowngradeAction.Downgrade)
    assertEquals(UpgradeDowngradeAction.fromString("main"), UpgradeDowngradeAction.Maintained)
    assertEquals(UpgradeDowngradeAction.fromString("reit"), UpgradeDowngradeAction.Reiterated)
    assertEquals(UpgradeDowngradeAction.fromString("init"), UpgradeDowngradeAction.Initiation)
  }

  test("UpgradeDowngradeAction.fromString should handle unknown actions with Other") {
    val other = UpgradeDowngradeAction.fromString("suspend")
    assertEquals(other, UpgradeDowngradeAction.Other("suspend"))
    assertEquals(other.value, "suspend")
  }

  test("UpgradeDowngrade ordering should sort most recent first") {
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

  test("EarningsEstimate estimateRange should calculate high minus low") {
    val range = earningsEst.estimateRange
    assert(range.isDefined)
    assert(Math.abs(range.get - 0.80) < 0.001)
  }

  test("EarningsEstimate estimateSpreadPercent should calculate spread relative to average") {
    val spread = earningsEst.estimateSpreadPercent
    assert(spread.isDefined)
    // 0.80 / 2.04 * 100 = 39.21...
    assert(spread.get > 39.0 && spread.get < 40.0)
  }

  test("EarningsEstimate estimateSpreadPercent should handle zero average") {
    val zeroAvg = earningsEst.copy(avg = Some(0.0))
    assertEquals(zeroAvg.estimateSpreadPercent, None)
  }

  test("EarningsEstimate isQuarterly should identify quarterly periods") {
    assert(earningsEst.isQuarterly)
    assert(!earningsEst.isYearly)
  }

  test("EarningsEstimate isYearly should identify yearly periods") {
    val yearly = earningsEst.copy(period = "0y")
    assert(yearly.isYearly)
    assert(!yearly.isQuarterly)
  }

  test("EarningsEstimate estimateRange should return None when data is missing") {
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

  test("RevenueEstimate estimateRange should calculate high minus low") {
    val range = revenueEst.estimateRange
    assert(range.isDefined)
    assertEquals(range.get, 55838000000L - 48955000000L)
  }

  test("RevenueEstimate isQuarterly should identify quarterly periods") {
    assert(revenueEst.isQuarterly)
    assert(!revenueEst.isYearly)
  }

  test("RevenueEstimate isYearly should identify yearly periods") {
    val yearly = revenueEst.copy(period = "+1y")
    assert(yearly.isYearly)
    assert(!yearly.isQuarterly)
  }

  test("RevenueEstimate estimateRange should return None when data is missing") {
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

  test("changeSevenDays should calculate difference") {
    val change = epsTrend.changeSevenDays
    assert(change.isDefined)
    assert(Math.abs(change.get - 0.02) < 0.001)
  }

  test("changeThirtyDays should calculate difference") {
    val change = epsTrend.changeThirtyDays
    assert(change.isDefined)
    assert(Math.abs(change.get - 0.04) < 0.001)
  }

  test("changeNinetyDays should calculate difference") {
    val change = epsTrend.changeNinetyDays
    assert(change.isDefined)
    assert(Math.abs(change.get - (-0.03)) < 0.001)
  }

  test("isTrendingUp should return true for positive 30-day change") {
    assertEquals(epsTrend.isTrendingUp, Some(true))
  }

  test("isTrendingUp should return false for negative 30-day change") {
    val declining = epsTrend.copy(current = Some(1.95))
    assertEquals(declining.isTrendingUp, Some(false))
  }

  test("EpsTrend change methods should return None when data is missing") {
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

  test("netRevisions30Days should calculate up minus down") {
    assertEquals(epsRevisions.netRevisions30Days, Some(8))
  }

  test("isPositiveTrend should return true when more ups") {
    assertEquals(epsRevisions.isPositiveTrend, Some(true))
  }

  test("isPositiveTrend should return false when more downs") {
    val negative = epsRevisions.copy(upLast30Days = Some(1), downLast30Days = Some(5))
    assertEquals(negative.isPositiveTrend, Some(false))
  }

  test("netRevisions30Days should return None when data is missing") {
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

  test("isBeat should return true when positive difference") {
    assertEquals(earningsHistoryEntry.isBeat, Some(true))
    assertEquals(earningsHistoryEntry.isMiss, Some(false))
  }

  test("isMiss should return true when negative difference") {
    val miss = earningsHistoryEntry.copy(epsDifference = Some(-0.05))
    assertEquals(miss.isMiss, Some(true))
    assertEquals(miss.isBeat, Some(false))
  }

  test("isMet should return true when zero difference") {
    val met = earningsHistoryEntry.copy(epsDifference = Some(0.0))
    assertEquals(met.isMet, Some(true))
    assertEquals(met.isBeat, Some(false))
    assertEquals(met.isMiss, Some(false))
  }

  test("EarningsHistory isBeat should return None when data is missing") {
    val noData = earningsHistoryEntry.copy(epsDifference = None)
    assertEquals(noData.isBeat, None)
    assertEquals(noData.isMiss, None)
    assertEquals(noData.isMet, None)
  }

  test("EarningsHistory ordering should sort most recent quarter first") {
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

  test("isOutperforming should return true when stock growth exceeds index") {
    assertEquals(growthEst.isOutperforming, Some(true))
  }

  test("isOutperforming should return false when stock underperforms") {
    val underperforming = growthEst.copy(stockGrowth = Some(0.05))
    assertEquals(underperforming.isOutperforming, Some(false))
  }

  test("growthDifferential should calculate difference") {
    val diff = growthEst.growthDifferential
    assert(diff.isDefined)
    assert(Math.abs(diff.get - 0.04) < 0.001)
  }

  test("isOutperforming should return None when data is missing") {
    val noStock = growthEst.copy(stockGrowth = None)
    assertEquals(noStock.isOutperforming, None)
    assertEquals(noStock.growthDifferential, None)

    val noIndex = growthEst.copy(indexGrowth = None)
    assertEquals(noIndex.isOutperforming, None)
    assertEquals(noIndex.growthDifferential, None)
  }

  // --- AnalystData tests ---

  test("isEmpty should return true for empty AnalystData") {
    assert(AnalystData.empty.isEmpty)
  }

  test("nonEmpty should return true when priceTargets is present") {
    val withTargets = AnalystData.empty.copy(priceTargets = Some(priceTargets))
    assert(withTargets.nonEmpty)
  }

  test("nonEmpty should return true when recommendations is present") {
    val withRecs = AnalystData.empty.copy(recommendations = List(recTrend))
    assert(withRecs.nonEmpty)
  }

  test("nonEmpty should return true when earningsEstimates is present") {
    val withEstimates = AnalystData.empty.copy(earningsEstimates = List(earningsEst))
    assert(withEstimates.nonEmpty)
  }

  test("nonEmpty should return true when upgradeDowngradeHistory is present") {
    val withHistory = AnalystData.empty.copy(upgradeDowngradeHistory = List(upgrade))
    assert(withHistory.nonEmpty)
  }

  test("nonEmpty should return true when earningsHistory is present") {
    val withEarnings = AnalystData.empty.copy(earningsHistory = List(earningsHistoryEntry))
    assert(withEarnings.nonEmpty)
  }

  test("nonEmpty should return true when growthEstimates is present") {
    val withGrowth = AnalystData.empty.copy(growthEstimates = List(growthEst))
    assert(withGrowth.nonEmpty)
  }

  test("currentRecommendation should find period 0m") {
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

  test("currentRecommendation should return None when no 0m period") {
    val data = AnalystData.empty.copy(recommendations =
      List(
        recTrend.copy(period = "-1m"),
        recTrend.copy(period = "-2m")
      )
    )
    assertEquals(data.currentRecommendation, None)
  }

  test("currentQuarterEarningsEstimate should find period 0q") {
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

  test("currentQuarterRevenueEstimate should find period 0q") {
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

  test("currentYearEarningsEstimate should find period 0y") {
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

  test("consecutiveBeats should count consecutive beats from most recent") {
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

  test("consecutiveBeats should return 0 when most recent is a miss") {
    val history = List(
      EarningsHistory(LocalDate.of(2024, 3, 31), "-1q", Some(1.3), Some(1.4), Some(-0.1), Some(-7.0)),
      EarningsHistory(LocalDate.of(2023, 12, 31), "-2q", Some(1.3), Some(1.2), Some(0.1), Some(8.0))
    )
    val data = AnalystData.empty.copy(earningsHistory = history)
    assertEquals(data.consecutiveBeats, 0)
  }

  test("recentUpgrades should filter and limit upgrades") {
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

  test("recentDowngrades should filter and limit downgrades") {
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

  test("earningsBeatRate should calculate percentage of beats") {
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

  test("earningsBeatRate should return None for empty history") {
    assertEquals(AnalystData.empty.earningsBeatRate, None)
  }

  test("earningsBeatRate should return None when all epsDifference is None") {
    val history = List(
      EarningsHistory(LocalDate.of(2024, 3, 31), "-1q", None, None, None, None)
    )
    val data = AnalystData.empty.copy(earningsHistory = history)
    assertEquals(data.earningsBeatRate, None)
  }

  test("empty AnalystData should return None for all accessors") {
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
