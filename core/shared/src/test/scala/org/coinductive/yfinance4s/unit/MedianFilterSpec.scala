package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.PriceRepair

class MedianFilterSpec extends FunSuite {

  test("medians a 3x3 neighbourhood with wrap-around at the top edge") {
    val matrix = Vector(
      Array.fill(5)(2.0),
      Array.fill(5)(4.0),
      Array.fill(5)(9.0)
    )
    // Every window spans all three rows via wrap, so every median is 4.0. A clamped boundary would
    // give 2.0 at row 0 and 9.0 at row 2.
    val expected = Vector.fill(3)(Vector.fill(5)(4.0))
    assertEquals(PriceRepair.medianFilter3x3Wrap(matrix).map(_.toSeq), expected.map(_.toSeq))
  }

  test("wraps the column axis so High neighbours Adj Close") {
    val matrix = Vector(Array(10.0, 20.0, 30.0, 40.0, 50.0))
    // Single row: each window is three copies of the wrapped column triple. Column 0 (High) pulls in
    // column 4 (Adj Close): median of {50, 10, 20} = 20; a clamped boundary would give 10.
    val expected = Vector(Vector(20.0, 20.0, 30.0, 40.0, 40.0))
    assertEquals(PriceRepair.medianFilter3x3Wrap(matrix).map(_.toSeq), expected.map(_.toSeq))
  }

  test("leaves an isolated 100x spike out of its own median") {
    val normalRow = Array(10.4, 10.0, 9.6, 10.2, 10.2)
    val spikedRow = Array(10.4, 10.0, 9.6, 1020.0, 10.2)
    val matrix = Vector(normalRow, normalRow, spikedRow, normalRow, normalRow)
    val medians = PriceRepair.medianFilter3x3Wrap(matrix)
    assertEquals(medians(2)(3), 10.2)
  }

  test("rounds half to even like numpy") {
    // 90 / 20 = 4.5: banker's rounding gives 4 (bucket 80, not flagged); half-up would give 5
    // (bucket 100, falsely flagged).
    assertEquals(PriceRepair.bucketedRatio(90.0), 80.0)
    assertEquals(PriceRepair.bucketedRatio(90.1), 100.0)
    assertEquals(PriceRepair.bucketedRatio(100.0), 100.0)
    assertEquals(PriceRepair.bucketedRatio(109.9), 100.0)
  }

  test("medians an even-count set as the mean of the two middle elements") {
    assertEquals(PriceRepair.medianNumpy(Vector(1.0, 1.0, 100.0, 100.0)), 50.5)
    assertEquals(PriceRepair.medianNumpy(Vector(3.0, 1.0, 2.0)), 2.0)
  }

  test("interpolates percentiles linearly like numpy") {
    assertEquals(PriceRepair.percentileLinear(Vector(1.0, 2.0, 3.0, 4.0), 25.0), 1.75)
    assertEquals(PriceRepair.percentileLinear(Vector(1.0, 2.0, 3.0, 4.0), 75.0), 3.25)
  }

  test("computes the population standard deviation") {
    assertEquals(PriceRepair.populationStd(Vector(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)), 2.0)
  }
}
