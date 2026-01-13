package org.coinductive.yfinance4s.models

import java.time.LocalDate

/** Type of insider ownership. */
sealed trait OwnershipType {
  def value: String
}

object OwnershipType {
  case object Direct extends OwnershipType { val value = "D" }
  case object Indirect extends OwnershipType { val value = "I" }

  def fromString(s: String): OwnershipType = s.toUpperCase match {
    case "D" => Direct
    case "I" => Indirect
    case _   => Direct // Default to direct if unknown
  }
}

/** An insider transaction (buy or sell).
  *
  * @param filerName
  *   Name of the insider
  * @param filerRelation
  *   Role/title (e.g., "Chief Executive Officer")
  * @param transactionDate
  *   Date of the transaction
  * @param shares
  *   Number of shares (negative for sales)
  * @param value
  *   Total transaction value in USD
  * @param transactionText
  *   Human-readable description of the transaction
  * @param ownershipType
  *   Direct or indirect ownership
  * @param filerUrl
  *   URL to SEC filing (may be empty)
  */
final case class InsiderTransaction(
    filerName: String,
    filerRelation: String,
    transactionDate: LocalDate,
    shares: Long,
    value: Option[Long],
    transactionText: String,
    ownershipType: OwnershipType,
    filerUrl: Option[String]
) {

  /** True if this is a purchase transaction */
  def isPurchase: Boolean = shares > 0

  /** True if this is a sale transaction */
  def isSale: Boolean = shares < 0

  /** Absolute number of shares traded */
  def sharesTraded: Long = Math.abs(shares)

  /** Estimated price per share (if value is available) */
  def pricePerShare: Option[Double] =
    value.filter(_ > 0).map(v => v.toDouble / sharesTraded)
}

object InsiderTransaction {

  /** Ordering by transaction date (most recent first) */
  implicit val ordering: Ordering[InsiderTransaction] =
    Ordering.by[InsiderTransaction, LocalDate](_.transactionDate).reverse
}
