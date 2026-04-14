package org.coinductive.yfinance4s.unit

import cats.syntax.show.*
import munit.FunSuite
import org.coinductive.yfinance4s.models.*

class IndustryDataSpec extends FunSuite {

  // --- IndustryOverview ---

  private val overview = IndustryOverview(
    companiesCount = Some(75),
    marketCap = Some(5000000000000L),
    messageBoardId = Some("finmb_xyz"),
    description = Some("The semiconductors industry..."),
    marketWeight = Some(0.089),
    employeeCount = Some(1200000L)
  )

  test("calculates industry market weight as percentage") {
    val percent = overview.marketWeightPercent
    assert(percent.isDefined)
    assert(Math.abs(percent.get - 8.9) < 0.001)
  }

  test("returns no market weight percent when absent") {
    assertEquals(IndustryOverview.empty.marketWeightPercent, None)
  }

  // --- IndustrySectorInfo ---

  test("converts sector info to SectorKey") {
    val info = IndustrySectorInfo(sectorKey = "technology", sectorName = "Technology")
    assertEquals(info.toSectorKey, SectorKey("technology"))
  }

  // --- TopPerformingCompany ---

  private val performer = TopPerformingCompany(
    symbol = "NVDA",
    name = Some("NVIDIA Corporation"),
    ytdReturn = Some(0.42),
    lastPrice = Some(100.0),
    targetPrice = Some(120.0)
  )

  test("converts performer symbol to ticker") {
    assertEquals(performer.toTicker, Ticker("NVDA"))
  }

  test("calculates performer ytd return percent") {
    val percent = performer.ytdReturnPercent
    assert(percent.isDefined)
    assert(Math.abs(percent.get - 42.0) < 0.001)
  }

  test("computes implied upside from last and target prices") {
    val upside = performer.impliedUpside
    assert(upside.isDefined)
    assert(Math.abs(upside.get - 0.20) < 0.001)
  }

  test("computes implied upside as percentage") {
    val percent = performer.impliedUpsidePercent
    assert(percent.isDefined)
    assert(Math.abs(percent.get - 20.0) < 0.001)
  }

  test("returns no implied upside when last price is zero") {
    val p = performer.copy(lastPrice = Some(0.0))
    assertEquals(p.impliedUpside, None)
  }

  test("returns no implied upside when last price is negative") {
    val p = performer.copy(lastPrice = Some(-10.0))
    assertEquals(p.impliedUpside, None)
  }

  test("returns no implied upside when last price is missing") {
    val p = performer.copy(lastPrice = None)
    assertEquals(p.impliedUpside, None)
  }

  test("returns no implied upside when target price is missing") {
    val p = performer.copy(targetPrice = None)
    assertEquals(p.impliedUpside, None)
  }

  test("sorts performers by ytd return descending") {
    val list = List(
      performer.copy(symbol = "A", ytdReturn = Some(0.10)),
      performer.copy(symbol = "B", ytdReturn = Some(0.30)),
      performer.copy(symbol = "C", ytdReturn = Some(0.20))
    )
    assertEquals(list.sorted.map(_.symbol), List("B", "C", "A"))
  }

  test("sorts performers with None ytd return last") {
    val list = List(
      performer.copy(symbol = "A", ytdReturn = None),
      performer.copy(symbol = "B", ytdReturn = Some(0.30)),
      performer.copy(symbol = "C", ytdReturn = Some(0.10))
    )
    assertEquals(list.sorted.map(_.symbol), List("B", "C", "A"))
  }

  // --- TopGrowthCompany ---

  private val grower = TopGrowthCompany(
    symbol = "NVDA",
    name = Some("NVIDIA Corporation"),
    ytdReturn = Some(0.42),
    growthEstimate = Some(0.28)
  )

  test("converts grower symbol to ticker") {
    assertEquals(grower.toTicker, Ticker("NVDA"))
  }

  test("calculates grower ytd return percent") {
    val percent = grower.ytdReturnPercent
    assert(percent.isDefined)
    assert(Math.abs(percent.get - 42.0) < 0.001)
  }

  test("calculates growth estimate percent") {
    val percent = grower.growthEstimatePercent
    assert(percent.isDefined)
    assert(Math.abs(percent.get - 28.0) < 0.001)
  }

  test("sorts growers by growth estimate descending") {
    val list = List(
      grower.copy(symbol = "A", growthEstimate = Some(0.10)),
      grower.copy(symbol = "B", growthEstimate = Some(0.30)),
      grower.copy(symbol = "C", growthEstimate = Some(0.20))
    )
    assertEquals(list.sorted.map(_.symbol), List("B", "C", "A"))
  }

  test("sorts growers with None growth estimate last") {
    val list = List(
      grower.copy(symbol = "A", growthEstimate = None),
      grower.copy(symbol = "B", growthEstimate = Some(0.30)),
      grower.copy(symbol = "C", growthEstimate = Some(0.10))
    )
    assertEquals(list.sorted.map(_.symbol), List("B", "C", "A"))
  }

  // --- IndustryData ---

  private val topCompany = TopCompany(
    symbol = "NVDA",
    name = Some("NVIDIA Corporation"),
    rating = Some("Buy"),
    marketWeight = Some(0.35)
  )

  private val data = IndustryData(
    key = IndustryKey("semiconductors"),
    name = "Semiconductors",
    sectorKey = "technology",
    sectorName = "Technology",
    symbol = Some("^GSPC-SEMI"),
    overview = Some(overview),
    topCompanies = List(
      topCompany,
      topCompany.copy(symbol = "AMD", name = Some("AMD"), marketWeight = Some(0.20)),
      topCompany.copy(symbol = "INTC", name = Some("Intel"), marketWeight = Some(0.15))
    ),
    topPerformingCompanies = List(
      performer.copy(symbol = "A", ytdReturn = Some(0.10)),
      performer.copy(symbol = "B", ytdReturn = Some(0.30)),
      performer.copy(symbol = "C", ytdReturn = Some(0.20))
    ),
    topGrowthCompanies = List(
      grower.copy(symbol = "X", growthEstimate = Some(0.10)),
      grower.copy(symbol = "Y", growthEstimate = Some(0.30)),
      grower.copy(symbol = "Z", growthEstimate = Some(0.20))
    )
  )

  private val minimal = IndustryData(
    key = IndustryKey("test"),
    name = "Test",
    sectorKey = "technology",
    sectorName = "Technology"
  )

  test("is non-empty when overview is present") {
    assert(data.nonEmpty)
  }

  test("is non-empty when only top companies are present") {
    val d = minimal.copy(topCompanies = List(topCompany))
    assert(d.nonEmpty)
  }

  test("is non-empty when only top performing companies are present") {
    val d = minimal.copy(topPerformingCompanies = List(performer))
    assert(d.nonEmpty)
  }

  test("is non-empty when only top growth companies are present") {
    val d = minimal.copy(topGrowthCompanies = List(grower))
    assert(d.nonEmpty)
  }

  test("is empty when all collections empty and overview absent") {
    assert(minimal.isEmpty)
  }

  test("derives sector info from required fields") {
    assertEquals(data.sectorInfo, IndustrySectorInfo("technology", "Technology"))
  }

  test("sector info round-trips to SectorKey") {
    assertEquals(data.sectorInfo.toSectorKey, SectorKey("technology"))
  }

  test("returns total market weight from overview") {
    assertEquals(data.totalMarketWeight, Some(0.089))
  }

  test("returns total market weight as percentage") {
    val percent = data.totalMarketWeightPercent
    assert(percent.isDefined)
    assert(Math.abs(percent.get - 8.9) < 0.001)
  }

  test("returns total companies from overview") {
    assertEquals(data.totalCompanies, Some(75))
  }

  test("returns total market cap from overview") {
    assertEquals(data.totalMarketCap, Some(5000000000000L))
  }

  test("returns description from overview") {
    assertEquals(data.description, Some("The semiconductors industry..."))
  }

  test("returns no description when overview is absent") {
    assertEquals(minimal.description, None)
  }

  test("returns top N companies by market weight descending") {
    val top2 = data.topCompaniesByWeight(2)
    assertEquals(top2.map(_.symbol), List("NVDA", "AMD"))
  }

  test("returns all companies when N exceeds count") {
    val allTop = data.topCompaniesByWeight(10)
    assertEquals(allTop.size, 3)
  }

  test("returns top N performers by ytd return descending") {
    val top2 = data.topPerformersByYtd(2)
    assertEquals(top2.map(_.symbol), List("B", "C"))
  }

  test("returns top N growth companies by growth estimate descending") {
    val top2 = data.topGrowthByEstimate(2)
    assertEquals(top2.map(_.symbol), List("Y", "Z"))
  }

  test("finds top company by symbol") {
    val found = data.findTopCompany("AMD")
    assert(found.isDefined)
    assertEquals(found.get.name, Some("AMD"))
  }

  test("returns None for unknown top company symbol") {
    assertEquals(data.findTopCompany("GOOGL"), None)
  }

  // --- IndustryKey ---

  test("displays industry key as its string value") {
    assertEquals(IndustryKey("software-infrastructure").show, "software-infrastructure")
  }
}
