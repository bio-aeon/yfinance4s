package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.{InsiderTransaction, OwnershipType}

import java.time.LocalDate

class InsiderTransactionSpec extends FunSuite {

  test("identifies purchase when shares are positive") {
    val purchase = InsiderTransaction(
      filerName = "John Doe",
      filerRelation = "CEO",
      transactionDate = LocalDate.of(2024, 1, 15),
      shares = 10000L,
      value = Some(1500000L),
      transactionText = "Purchase at $150",
      ownershipType = OwnershipType.Direct,
      filerUrl = None
    )

    assert(purchase.isPurchase)
    assert(!purchase.isSale)
  }

  test("identifies sale when shares are negative") {
    val sale = InsiderTransaction(
      filerName = "Jane Smith",
      filerRelation = "CFO",
      transactionDate = LocalDate.of(2024, 1, 20),
      shares = -5000L,
      value = Some(750000L),
      transactionText = "Sale at $150",
      ownershipType = OwnershipType.Direct,
      filerUrl = None
    )

    assert(sale.isSale)
    assert(!sale.isPurchase)
  }

  test("reports absolute shares traded") {
    val sale = InsiderTransaction(
      filerName = "Test",
      filerRelation = "Director",
      transactionDate = LocalDate.now(),
      shares = -5000L,
      value = Some(500000L),
      transactionText = "Sale",
      ownershipType = OwnershipType.Direct,
      filerUrl = None
    )

    assertEquals(sale.sharesTraded, 5000L)
  }

  test("calculates price per share from value and shares") {
    val transaction = InsiderTransaction(
      filerName = "Test",
      filerRelation = "CEO",
      transactionDate = LocalDate.now(),
      shares = -1000L,
      value = Some(150000L),
      transactionText = "Sale at $150",
      ownershipType = OwnershipType.Direct,
      filerUrl = None
    )

    assertEquals(transaction.pricePerShare, Some(150.0))
  }

  test("returns no price per share when value is absent") {
    val transaction = InsiderTransaction(
      filerName = "Test",
      filerRelation = "CEO",
      transactionDate = LocalDate.now(),
      shares = 1000L,
      value = None,
      transactionText = "Grant",
      ownershipType = OwnershipType.Direct,
      filerUrl = None
    )

    assertEquals(transaction.pricePerShare, None)
  }

  test("returns no price per share when value is zero") {
    val transaction = InsiderTransaction(
      filerName = "Test",
      filerRelation = "CEO",
      transactionDate = LocalDate.now(),
      shares = 1000L,
      value = Some(0L),
      transactionText = "Grant",
      ownershipType = OwnershipType.Direct,
      filerUrl = None
    )

    assertEquals(transaction.pricePerShare, None)
  }

  test("sorts by transaction date descending") {
    val t1 = InsiderTransaction("A", "CEO", LocalDate.of(2024, 1, 1), 100, None, "", OwnershipType.Direct, None)
    val t2 = InsiderTransaction("B", "CFO", LocalDate.of(2024, 3, 1), 200, None, "", OwnershipType.Direct, None)
    val t3 = InsiderTransaction("C", "COO", LocalDate.of(2024, 2, 1), 150, None, "", OwnershipType.Direct, None)

    val sorted = List(t1, t2, t3).sorted

    assertEquals(sorted.map(_.filerName), List("B", "C", "A"))
  }

  test("parses ownership type from string") {
    assertEquals(OwnershipType.fromString("D"), OwnershipType.Direct)
    assertEquals(OwnershipType.fromString("I"), OwnershipType.Indirect)
    assertEquals(OwnershipType.fromString("d"), OwnershipType.Direct)
    assertEquals(OwnershipType.fromString("i"), OwnershipType.Indirect)
    assertEquals(OwnershipType.fromString("unknown"), OwnershipType.Direct) // Default
  }
}
