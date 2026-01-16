package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.MajorHolders

class MajorHoldersSpec extends FunSuite {

  test("retailPercentHeld should calculate remaining percentage") {
    val holders = MajorHolders(
      insidersPercentHeld = 0.05,
      institutionsPercentHeld = 0.60,
      institutionsFloatPercentHeld = 0.62,
      institutionsCount = 1000
    )

    // 1.0 - 0.05 - 0.60 = 0.35
    assert(Math.abs(holders.retailPercentHeld - 0.35) < 0.001)
  }

  test("isInstitutionallyDominated should return true when institutions hold >50%") {
    val dominated = MajorHolders(0.01, 0.60, 0.62, 1000)
    val notDominated = MajorHolders(0.01, 0.40, 0.42, 500)

    assert(dominated.isInstitutionallyDominated)
    assert(!notDominated.isInstitutionallyDominated)
  }

  test("hasSignificantInsiderOwnership should return true when insiders hold >5%") {
    val significant = MajorHolders(0.10, 0.50, 0.52, 1000)
    val notSignificant = MajorHolders(0.02, 0.60, 0.62, 1000)

    assert(significant.hasSignificantInsiderOwnership)
    assert(!notSignificant.hasSignificantInsiderOwnership)
  }

  test("retailPercentHeld should not be negative") {
    // Edge case: percentages exceed 100% (can happen with rounding)
    val holders = MajorHolders(0.50, 0.60, 0.62, 1000)

    assertEquals(holders.retailPercentHeld, 0.0)
  }

  test("isInstitutionallyDominated boundary at exactly 50%") {
    val atBoundary = MajorHolders(0.01, 0.50, 0.52, 500)
    val justAbove = MajorHolders(0.01, 0.501, 0.52, 500)

    assert(!atBoundary.isInstitutionallyDominated)
    assert(justAbove.isInstitutionallyDominated)
  }

  test("hasSignificantInsiderOwnership boundary at exactly 5%") {
    val atBoundary = MajorHolders(0.05, 0.50, 0.52, 1000)
    val justAbove = MajorHolders(0.051, 0.50, 0.52, 1000)

    assert(!atBoundary.hasSignificantInsiderOwnership)
    assert(justAbove.hasSignificantInsiderOwnership)
  }
}
