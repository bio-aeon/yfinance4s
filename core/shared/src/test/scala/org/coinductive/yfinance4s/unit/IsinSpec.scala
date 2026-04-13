package org.coinductive.yfinance4s.unit

import cats.syntax.show.*
import munit.FunSuite
import org.coinductive.yfinance4s.models.Isin

class IsinSpec extends FunSuite {

  // --- Validation: valid ISINs ---

  private val validIsins = List(
    "US0378331005", // Apple
    "US5949181045", // Microsoft
    "US67066G1040", // NVIDIA
    "US88160R1014", // Tesla
    "GB0002374006", // Diageo
    "DE0007164600", // SAP
    "JP3633400001", // Toyota
    "FR0000120271" // TotalEnergies
  )

  test("validates a corpus of known-valid ISINs") {
    validIsins.foreach { isin =>
      val result = Isin.validate(isin)
      assert(result.isRight, s"Expected valid: $isin, got: $result")
      assertEquals(result, Right(Isin(isin)))
    }
  }

  // --- Validation: normalisation ---

  test("normalises lowercase input to uppercase") {
    assertEquals(Isin.validate("us0378331005"), Right(Isin("US0378331005")))
  }

  test("normalises mixed case input to uppercase") {
    assertEquals(Isin.validate("Us67066g1040"), Right(Isin("US67066G1040")))
  }

  test("trims leading and trailing whitespace") {
    assertEquals(Isin.validate("  US0378331005  "), Right(Isin("US0378331005")))
  }

  // --- Validation: length errors ---

  test("rejects ISIN that is too short") {
    val result = Isin.validate("US037833100")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("12 characters")), s"Unexpected error: $result")
  }

  test("rejects ISIN that is too long") {
    val result = Isin.validate("US03783310055")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("12 characters")), s"Unexpected error: $result")
  }

  test("rejects empty string") {
    val result = Isin.validate("")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("12 characters")), s"Unexpected error: $result")
  }

  // --- Validation: pattern errors ---

  test("rejects ISIN with special characters") {
    val result = Isin.validate("US037833100!")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("pattern")), s"Unexpected error: $result")
  }

  test("rejects ISIN with numeric country code") {
    val result = Isin.validate("123456789012")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("pattern")), s"Unexpected error: $result")
  }

  // --- Validation: check digit errors ---

  test("rejects ISIN with incorrect check digit") {
    val result = Isin.validate("US0378331009")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("check digit")), s"Unexpected error: $result")
  }

  // --- isValid convenience method ---

  test("isValid returns true for valid ISIN") {
    assert(Isin.isValid("US0378331005"))
  }

  test("isValid returns false for invalid ISIN") {
    assert(!Isin.isValid("INVALID"))
  }

  // --- Check digit computation ---

  test("computes correct check digits for known ISINs") {
    val prefixToExpected = List(
      "US037833100" -> 5, // Apple
      "US594918104" -> 5, // Microsoft
      "US67066G104" -> 0, // NVIDIA (boundary: check digit 0)
      "US88160R101" -> 4, // Tesla
      "GB000237400" -> 6, // Diageo
      "DE000716460" -> 0, // SAP
      "JP363340000" -> 1, // Toyota
      "FR000012027" -> 1 // TotalEnergies
    )
    prefixToExpected.foreach { case (prefix, expected) =>
      assertEquals(Isin.computeCheckDigit(prefix), expected, s"Failed for prefix: $prefix")
    }
  }

  // --- Country code extraction ---

  test("extracts country code") {
    assertEquals(Isin.countryCode("US0378331005"), "US")
    assertEquals(Isin.countryCode("GB0002374006"), "GB")
  }

  test("uppercases country code from lowercase input") {
    assertEquals(Isin.countryCode("de0007164600"), "DE")
  }

  // --- Show instance ---

  test("displays ISIN as its string value") {
    assertEquals(Isin("US0378331005").show, "US0378331005")
  }
}
