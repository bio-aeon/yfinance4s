package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.*

import java.time.LocalDate

class HoldersDataSpec extends FunSuite {

  test("isEmpty should return true for empty HoldersData") {
    assert(HoldersData.empty.isEmpty)
  }

  test("nonEmpty should return true when majorHolders is present") {
    val withMajor = HoldersData(
      majorHolders = Some(MajorHolders(0.05, 0.60, 0.62, 1000)),
      institutionalHolders = List.empty,
      mutualFundHolders = List.empty,
      insiderTransactions = List.empty,
      insiderRoster = List.empty
    )

    assert(withMajor.nonEmpty)
  }

  test("nonEmpty should return true when institutionalHolders is present") {
    val withInstitutional = HoldersData(
      majorHolders = None,
      institutionalHolders = List(InstitutionalHolder("A", LocalDate.now(), 0.05, 100, 1000)),
      mutualFundHolders = List.empty,
      insiderTransactions = List.empty,
      insiderRoster = List.empty
    )

    assert(withInstitutional.nonEmpty)
  }

  test("nonEmpty should return true when mutualFundHolders is present") {
    val withFunds = HoldersData(
      majorHolders = None,
      institutionalHolders = List.empty,
      mutualFundHolders = List(MutualFundHolder("A", LocalDate.now(), 0.02, 50, 500)),
      insiderTransactions = List.empty,
      insiderRoster = List.empty
    )

    assert(withFunds.nonEmpty)
  }

  test("nonEmpty should return true when insiderTransactions is present") {
    val withTransactions = HoldersData(
      majorHolders = None,
      institutionalHolders = List.empty,
      mutualFundHolders = List.empty,
      insiderTransactions = List(
        InsiderTransaction("A", "CEO", LocalDate.now(), 100, None, "", OwnershipType.Direct, None)
      ),
      insiderRoster = List.empty
    )

    assert(withTransactions.nonEmpty)
  }

  test("nonEmpty should return true when insiderRoster is present") {
    val withRoster = HoldersData(
      majorHolders = None,
      institutionalHolders = List.empty,
      mutualFundHolders = List.empty,
      insiderTransactions = List.empty,
      insiderRoster = List(InsiderRosterEntry("A", "CEO", None, None, Some(100L), None, None, None, None))
    )

    assert(withRoster.nonEmpty)
  }

  test("totalInstitutionalCount should sum both holder lists") {
    val data = HoldersData(
      majorHolders = None,
      institutionalHolders = List(
        InstitutionalHolder("A", LocalDate.now(), 0.05, 100, 1000),
        InstitutionalHolder("B", LocalDate.now(), 0.03, 50, 500)
      ),
      mutualFundHolders = List(
        MutualFundHolder("C", LocalDate.now(), 0.02, 30, 300)
      ),
      insiderTransactions = List.empty,
      insiderRoster = List.empty
    )

    assertEquals(data.totalInstitutionalCount, 3)
  }

  test("topInstitutionalPercentage should sum percentages") {
    val data = HoldersData(
      majorHolders = None,
      institutionalHolders = List(
        InstitutionalHolder("A", LocalDate.now(), 0.10, 100, 1000),
        InstitutionalHolder("B", LocalDate.now(), 0.05, 50, 500)
      ),
      mutualFundHolders = List.empty,
      insiderTransactions = List.empty,
      insiderRoster = List.empty
    )

    assert(Math.abs(data.topInstitutionalPercentage - 0.15) < 0.001)
  }

  test("topMutualFundPercentage should sum percentages") {
    val data = HoldersData(
      majorHolders = None,
      institutionalHolders = List.empty,
      mutualFundHolders = List(
        MutualFundHolder("A", LocalDate.now(), 0.03, 30, 300),
        MutualFundHolder("B", LocalDate.now(), 0.02, 20, 200)
      ),
      insiderTransactions = List.empty,
      insiderRoster = List.empty
    )

    assert(Math.abs(data.topMutualFundPercentage - 0.05) < 0.001)
  }

  test("insiderPurchases and insiderSales should filter correctly") {
    val purchase = InsiderTransaction("A", "CEO", LocalDate.now(), 1000, None, "Buy", OwnershipType.Direct, None)
    val sale = InsiderTransaction("B", "CFO", LocalDate.now(), -500, None, "Sell", OwnershipType.Direct, None)

    val data = HoldersData(
      majorHolders = None,
      institutionalHolders = List.empty,
      mutualFundHolders = List.empty,
      insiderTransactions = List(purchase, sale),
      insiderRoster = List.empty
    )

    assertEquals(data.insiderPurchases.size, 1)
    assertEquals(data.insiderSales.size, 1)
    assertEquals(data.insiderPurchases.head.filerName, "A")
    assertEquals(data.insiderSales.head.filerName, "B")
  }

  test("netInsiderShares should calculate net sentiment") {
    val purchase = InsiderTransaction("A", "CEO", LocalDate.now(), 1000, None, "Buy", OwnershipType.Direct, None)
    val sale = InsiderTransaction("B", "CFO", LocalDate.now(), -300, None, "Sell", OwnershipType.Direct, None)

    val data = HoldersData(
      majorHolders = None,
      institutionalHolders = List.empty,
      mutualFundHolders = List.empty,
      insiderTransactions = List(purchase, sale),
      insiderRoster = List.empty
    )

    assertEquals(data.netInsiderShares, 700L) // 1000 - 300
  }

  test("netInsiderShares with only sales should be negative") {
    val sale1 = InsiderTransaction("A", "CEO", LocalDate.now(), -1000, None, "Sell", OwnershipType.Direct, None)
    val sale2 = InsiderTransaction("B", "CFO", LocalDate.now(), -500, None, "Sell", OwnershipType.Direct, None)

    val data = HoldersData(
      majorHolders = None,
      institutionalHolders = List.empty,
      mutualFundHolders = List.empty,
      insiderTransactions = List(sale1, sale2),
      insiderRoster = List.empty
    )

    assertEquals(data.netInsiderShares, -1500L)
  }

  test("empty HoldersData should return zero for all aggregations") {
    val data = HoldersData.empty

    assertEquals(data.topInstitutionalPercentage, 0.0)
    assertEquals(data.topMutualFundPercentage, 0.0)
    assertEquals(data.totalInstitutionalCount, 0)
    assertEquals(data.netInsiderShares, 0L)
  }
}
