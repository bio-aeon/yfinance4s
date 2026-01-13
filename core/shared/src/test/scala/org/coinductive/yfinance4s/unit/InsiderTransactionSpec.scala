package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.{InsiderTransaction, OwnershipType}

import java.time.LocalDate

class InsiderTransactionSpec extends FunSuite {

  test("isPurchase should return true for positive shares") {
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

  test("isSale should return true for negative shares") {
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

  test("sharesTraded should return absolute value") {
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

  test("pricePerShare should calculate from value and shares") {
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

  test("pricePerShare should return None when value is None") {
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

  test("pricePerShare should return None when value is zero") {
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

  test("ordering should sort by date descending (most recent first)") {
    val t1 = InsiderTransaction("A", "CEO", LocalDate.of(2024, 1, 1), 100, None, "", OwnershipType.Direct, None)
    val t2 = InsiderTransaction("B", "CFO", LocalDate.of(2024, 3, 1), 200, None, "", OwnershipType.Direct, None)
    val t3 = InsiderTransaction("C", "COO", LocalDate.of(2024, 2, 1), 150, None, "", OwnershipType.Direct, None)

    val sorted = List(t1, t2, t3).sorted

    assertEquals(sorted.map(_.filerName), List("B", "C", "A"))
  }

  test("OwnershipType.fromString should parse correctly") {
    assertEquals(OwnershipType.fromString("D"), OwnershipType.Direct)
    assertEquals(OwnershipType.fromString("I"), OwnershipType.Indirect)
    assertEquals(OwnershipType.fromString("d"), OwnershipType.Direct)
    assertEquals(OwnershipType.fromString("i"), OwnershipType.Indirect)
    assertEquals(OwnershipType.fromString("unknown"), OwnershipType.Direct) // Default
  }

  test("OwnershipType.value should return correct string") {
    assertEquals(OwnershipType.Direct.value, "D")
    assertEquals(OwnershipType.Indirect.value, "I")
  }
}
