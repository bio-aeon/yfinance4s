package org.coinductive.yfinance4s.models

import java.time.LocalDate

/** An entry in the insider roster showing current positions.
  *
  * @param name
  *   Name of the insider
  * @param relation
  *   Role/title (e.g., "Chief Executive Officer")
  * @param latestTransactionDate
  *   Date of most recent transaction
  * @param latestTransactionType
  *   Description of most recent transaction (e.g., "Sale", "Purchase")
  * @param positionDirect
  *   Number of shares held directly
  * @param positionDirectDate
  *   Date of direct position report
  * @param positionIndirect
  *   Number of shares held indirectly (trusts, family)
  * @param positionIndirectDate
  *   Date of indirect position report
  * @param url
  *   URL to SEC profile (may be empty)
  */
final case class InsiderRosterEntry(
    name: String,
    relation: String,
    latestTransactionDate: Option[LocalDate],
    latestTransactionType: Option[String],
    positionDirect: Option[Long],
    positionDirectDate: Option[LocalDate],
    positionIndirect: Option[Long],
    positionIndirectDate: Option[LocalDate],
    url: Option[String]
) {

  /** Total shares held (direct + indirect) */
  def totalPosition: Long = positionDirect.getOrElse(0L) + positionIndirect.getOrElse(0L)

  /** True if the insider has any reported position */
  def hasPosition: Boolean = totalPosition > 0

  /** True if the insider has indirect holdings (e.g., through trusts) */
  def hasIndirectHoldings: Boolean = positionIndirect.exists(_ > 0)
}

object InsiderRosterEntry {

  /** Ordering by total position (largest first) */
  implicit val ordering: Ordering[InsiderRosterEntry] =
    Ordering.by[InsiderRosterEntry, Long](_.totalPosition).reverse
}
