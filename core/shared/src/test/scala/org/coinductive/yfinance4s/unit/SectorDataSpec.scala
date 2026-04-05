package org.coinductive.yfinance4s.unit

import cats.syntax.show.*
import munit.FunSuite
import org.coinductive.yfinance4s.models.*

class SectorDataSpec extends FunSuite {

  // --- SectorOverview tests ---

  private val overview = SectorOverview(
    companiesCount = Some(679),
    marketCap = Some(21000000000000L),
    messageBoardId = Some("finmb_123"),
    description = Some("Technology sector"),
    industriesCount = Some(12),
    marketWeight = Some(0.32),
    employeeCount = Some(4500000L)
  )

  test("calculates market weight as percentage") {
    val percent = overview.marketWeightPercent
    assert(percent.isDefined)
    assert(Math.abs(percent.get - 32.0) < 0.001)
  }

  test("returns no market weight percent when absent") {
    assertEquals(SectorOverview.empty.marketWeightPercent, None)
  }

  // --- TopCompany tests ---

  private val topCompany = TopCompany(
    symbol = "AAPL",
    name = Some("Apple Inc."),
    rating = Some("Hold"),
    marketWeight = Some(0.15)
  )

  test("converts top company symbol to ticker") {
    assertEquals(topCompany.toTicker, Ticker("AAPL"))
  }

  test("calculates top company market weight as percentage") {
    val percent = topCompany.marketWeightPercent
    assert(percent.isDefined)
    assert(Math.abs(percent.get - 15.0) < 0.001)
  }

  // --- SectorETF tests ---

  test("converts ETF symbol to ticker") {
    val etf = SectorETF("XLK", Some("Technology Select Sector SPDR Fund"))
    assertEquals(etf.toTicker, Ticker("XLK"))
  }

  // --- SectorMutualFund tests ---

  test("converts mutual fund symbol to ticker") {
    val fund = SectorMutualFund("FTCBX", Some("Fidelity Select Technology Portfolio"))
    assertEquals(fund.toTicker, Ticker("FTCBX"))
  }

  // --- SectorIndustry tests ---

  private val industry = SectorIndustry(
    key = "semiconductors",
    name = "Semiconductors",
    symbol = Some("^GSPC-SEMI"),
    marketWeight = Some(0.25)
  )

  test("calculates industry market weight as percentage") {
    val percent = industry.marketWeightPercent
    assert(percent.isDefined)
    assert(Math.abs(percent.get - 25.0) < 0.001)
  }

  test("sorts industries by market weight descending") {
    val industries = List(
      industry.copy(key = "a", marketWeight = Some(0.10)),
      industry.copy(key = "b", marketWeight = Some(0.25)),
      industry.copy(key = "c", marketWeight = Some(0.15))
    )
    val sorted = industries.sorted
    assertEquals(sorted.map(_.key), List("b", "c", "a"))
  }

  test("sorts industries with None market weight last") {
    val industries = List(
      industry.copy(key = "a", marketWeight = None),
      industry.copy(key = "b", marketWeight = Some(0.25))
    )
    val sorted = industries.sorted
    assertEquals(sorted.map(_.key), List("b", "a"))
  }

  // --- SectorData tests ---

  private val sectorData = SectorData(
    key = SectorKey.Technology,
    name = "Technology",
    symbol = Some("^GSPC-TECH"),
    overview = Some(overview),
    topCompanies = List(
      topCompany,
      topCompany.copy(symbol = "MSFT", name = Some("Microsoft"), marketWeight = Some(0.12)),
      topCompany.copy(symbol = "NVDA", name = Some("NVIDIA"), marketWeight = Some(0.10))
    ),
    topETFs = List(SectorETF("XLK", Some("SPDR Tech"))),
    topMutualFunds = List(SectorMutualFund("FTCBX", Some("Fidelity Tech"))),
    industries = List(
      industry,
      industry.copy(key = "software-infrastructure", name = "Software - Infrastructure", marketWeight = Some(0.20)),
      industry.copy(key = "consumer-electronics", name = "Consumer Electronics", marketWeight = Some(0.08))
    )
  )

  test("is non-empty when overview is present") {
    assert(sectorData.nonEmpty)
  }

  test("is non-empty when top companies are present but overview is absent") {
    val withCompanies = SectorData(
      key = SectorKey.Technology,
      name = "Technology",
      topCompanies = List(topCompany)
    )
    assert(withCompanies.nonEmpty)
  }

  test("is empty when no overview, companies, ETFs, or industries") {
    val minimal = SectorData(key = SectorKey.Technology, name = "Technology")
    assert(minimal.isEmpty)
  }

  test("returns industry count") {
    assertEquals(sectorData.industryCount, 3)
  }

  test("returns total market weight from overview") {
    assertEquals(sectorData.totalMarketWeight, Some(0.32))
  }

  test("returns total market weight as percentage") {
    val percent = sectorData.totalMarketWeightPercent
    assert(percent.isDefined)
    assert(Math.abs(percent.get - 32.0) < 0.001)
  }

  test("returns total companies from overview") {
    assertEquals(sectorData.totalCompanies, Some(679))
  }

  test("returns total market cap from overview") {
    assertEquals(sectorData.totalMarketCap, Some(21000000000000L))
  }

  test("returns sector description from overview") {
    assertEquals(sectorData.description, Some("Technology sector"))
  }

  test("returns no description when overview is absent") {
    val noOverview = SectorData(key = SectorKey.Technology, name = "Technology")
    assertEquals(noOverview.description, None)
  }

  test("returns industries sorted by market weight descending") {
    val byWeight = sectorData.industriesByWeight
    assertEquals(byWeight.map(_.key), List("semiconductors", "software-infrastructure", "consumer-electronics"))
  }

  test("finds industry by key") {
    val found = sectorData.findIndustry("semiconductors")
    assert(found.isDefined)
    assertEquals(found.get.name, "Semiconductors")
  }

  test("returns None for unknown industry key") {
    assertEquals(sectorData.findIndustry("nonexistent"), None)
  }

  test("returns top N companies by market weight") {
    val top2 = sectorData.topCompaniesByWeight(2)
    assertEquals(top2.size, 2)
    assertEquals(top2.map(_.symbol), List("AAPL", "MSFT"))
  }

  test("returns all companies when N exceeds count") {
    val topAll = sectorData.topCompaniesByWeight(10)
    assertEquals(topAll.size, 3)
  }

  // --- SectorKey tests ---

  test("predefined sector keys have correct values") {
    assertEquals(SectorKey.Technology.value, "technology")
    assertEquals(SectorKey.Healthcare.value, "healthcare")
    assertEquals(SectorKey.FinancialServices.value, "financial-services")
    assertEquals(SectorKey.RealEstate.value, "real-estate")
    assertEquals(SectorKey.ConsumerCyclical.value, "consumer-cyclical")
    assertEquals(SectorKey.BasicMaterials.value, "basic-materials")
  }

  test("all contains exactly 11 GICS sectors") {
    assertEquals(SectorKey.all.size, 11)
  }

  test("all sector keys are unique") {
    assertEquals(SectorKey.all.map(_.value).distinct.size, 11)
  }

  test("displays sector key as its string value") {
    assertEquals(SectorKey.Technology.show, "technology")
    assertEquals(SectorKey.FinancialServices.show, "financial-services")
  }

  // --- defaults ---

  test("has empty defaults when constructed with only key and name") {
    val data = SectorData(key = SectorKey.Technology, name = "Technology")
    assertEquals(data.industryCount, 0)
    assertEquals(data.totalMarketWeight, None)
    assertEquals(data.totalMarketWeightPercent, None)
    assertEquals(data.totalCompanies, None)
    assertEquals(data.totalMarketCap, None)
    assertEquals(data.description, None)
    assert(data.industriesByWeight.isEmpty)
    assertEquals(data.findIndustry("any"), None)
    assert(data.topCompaniesByWeight(5).isEmpty)
  }
}
