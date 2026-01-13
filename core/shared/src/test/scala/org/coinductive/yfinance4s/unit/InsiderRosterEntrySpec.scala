package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.InsiderRosterEntry

import java.time.LocalDate

class InsiderRosterEntrySpec extends FunSuite {

  test("totalPosition should sum direct and indirect holdings") {
    val entry = InsiderRosterEntry(
      name = "John Doe",
      relation = "CEO",
      latestTransactionDate = Some(LocalDate.now()),
      latestTransactionType = Some("Sale"),
      positionDirect = Some(100000L),
      positionDirectDate = Some(LocalDate.now()),
      positionIndirect = Some(50000L),
      positionIndirectDate = Some(LocalDate.now()),
      url = None
    )

    assertEquals(entry.totalPosition, 150000L)
  }

  test("totalPosition should handle None values") {
    val entry = InsiderRosterEntry(
      name = "Jane Smith",
      relation = "CFO",
      latestTransactionDate = None,
      latestTransactionType = None,
      positionDirect = Some(100000L),
      positionDirectDate = Some(LocalDate.now()),
      positionIndirect = None,
      positionIndirectDate = None,
      url = None
    )

    assertEquals(entry.totalPosition, 100000L)
  }

  test("totalPosition should return 0 when both positions are None") {
    val entry = InsiderRosterEntry(
      name = "Test User",
      relation = "Director",
      latestTransactionDate = None,
      latestTransactionType = None,
      positionDirect = None,
      positionDirectDate = None,
      positionIndirect = None,
      positionIndirectDate = None,
      url = None
    )

    assertEquals(entry.totalPosition, 0L)
  }

  test("hasPosition should return true when totalPosition > 0") {
    val withPosition = InsiderRosterEntry("A", "CEO", None, None, Some(100L), None, None, None, None)
    val withoutPosition = InsiderRosterEntry("B", "CFO", None, None, None, None, None, None, None)

    assert(withPosition.hasPosition)
    assert(!withoutPosition.hasPosition)
  }

  test("hasIndirectHoldings should detect indirect positions") {
    val withIndirect = InsiderRosterEntry("A", "CEO", None, None, Some(100L), None, Some(50L), None, None)
    val withoutIndirect = InsiderRosterEntry("B", "CFO", None, None, Some(100L), None, None, None, None)
    val zeroIndirect = InsiderRosterEntry("C", "COO", None, None, Some(100L), None, Some(0L), None, None)

    assert(withIndirect.hasIndirectHoldings)
    assert(!withoutIndirect.hasIndirectHoldings)
    assert(!zeroIndirect.hasIndirectHoldings)
  }

  test("ordering should sort by total position descending") {
    val e1 = InsiderRosterEntry("A", "CEO", None, None, Some(100L), None, Some(50L), None, None) // 150
    val e2 = InsiderRosterEntry("B", "CFO", None, None, Some(200L), None, None, None, None) // 200
    val e3 = InsiderRosterEntry("C", "COO", None, None, Some(50L), None, Some(25L), None, None) // 75

    val sorted = List(e1, e2, e3).sorted

    assertEquals(sorted.map(_.name), List("B", "A", "C"))
  }

  test("hasPosition with only indirect holdings") {
    val indirectOnly = InsiderRosterEntry("A", "CEO", None, None, None, None, Some(100L), None, None)

    assert(indirectOnly.hasPosition)
    assertEquals(indirectOnly.totalPosition, 100L)
  }
}
