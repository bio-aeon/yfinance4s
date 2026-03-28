package org.coinductive.yfinance4s.models

import cats.Show

/** An International Securities Identification Number (ISO 6166).
  *
  * An ISIN is a 12-character alphanumeric code that uniquely identifies a security. The format is: 2-letter country
  * code + 9 alphanumeric characters + 1 Luhn check digit.
  *
  * Use [[Isin.validate]] to parse and validate, or [[Isin.isValid]] for a boolean check.
  *
  * @param value
  *   The 12-character ISIN string (always stored uppercase)
  */
final case class Isin(value: String) extends AnyVal

object Isin {

  implicit val show: Show[Isin] = Show.show(_.value)

  private val IsinLength = 12
  private val IsinPattern = "^[A-Z]{2}[A-Z0-9]{9}[0-9]$".r

  /** Validates and constructs an [[Isin]] from a raw string.
    *
    * Checks:
    *   1. Length is exactly 12 characters
    *   1. First 2 characters are uppercase letters (country code)
    *   1. Characters 3-11 are uppercase alphanumeric
    *   1. Character 12 is a digit (check digit)
    *   1. Luhn check digit is correct
    *
    * @return
    *   Right(Isin) if valid, Left(error message) if invalid
    */
  def validate(value: String): Either[String, Isin] = {
    val upper = value.trim.toUpperCase
    if (upper.length != IsinLength)
      Left(s"ISIN must be $IsinLength characters, got ${upper.length}: '$value'")
    else if (IsinPattern.findFirstIn(upper).isEmpty)
      Left(s"ISIN must match pattern XX(alphanumeric x9)(digit): '$value'")
    else {
      val expectedCheckDigit = computeCheckDigit(upper.substring(0, IsinLength - 1))
      val actualCheckDigit = upper.last - '0'
      if (actualCheckDigit != expectedCheckDigit)
        Left(s"ISIN check digit mismatch: expected $expectedCheckDigit, got $actualCheckDigit in '$value'")
      else
        Right(Isin(upper))
    }
  }

  /** Returns true if the value is a syntactically valid ISIN. */
  def isValid(value: String): Boolean = validate(value).isRight

  /** Computes the Luhn check digit for an 11-character ISIN prefix.
    *
    * Converts letters to two-digit numbers (A=10 .. Z=35), concatenates all digits, then applies the standard Luhn
    * algorithm.
    */
  def computeCheckDigit(prefix: String): Int = {
    val digits = prefix.flatMap(charToDigits)
    val sum = digits.reverse.zipWithIndex.map { case (d, i) =>
      val posFromRight = i + 2 // position 1 is the check digit being computed
      if (posFromRight % 2 == 0) {
        val doubled = d * 2
        if (doubled > 9) doubled - 9 else doubled
      } else d
    }.sum
    (10 - (sum % 10)) % 10
  }

  /** Extracts the 2-character country code from an ISIN. Does not validate the ISIN -- call [[validate]] first if
    * needed.
    */
  def countryCode(isin: String): String = isin.take(2).toUpperCase

  private def charToDigits(c: Char): Seq[Int] =
    if (c.isDigit) Seq(c - '0')
    else {
      val n = c.toUpper - 'A' + 10
      Seq(n / 10, n % 10)
    }
}
