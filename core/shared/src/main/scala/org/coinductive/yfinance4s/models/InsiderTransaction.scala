package org.coinductive.yfinance4s.models

import enumeratum.values.{StringEnum, StringEnumEntry}

import java.time.LocalDate

/** Type of insider ownership. */
sealed abstract class OwnershipType(val value: String) extends StringEnumEntry

object OwnershipType extends StringEnum[OwnershipType] {
  case object Direct extends OwnershipType("D")
  case object Indirect extends OwnershipType("I")

  val values: IndexedSeq[OwnershipType] = findValues

  def fromString(s: String): OwnershipType =
    withValueOpt(s.toUpperCase).getOrElse(Direct)
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
