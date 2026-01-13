package org.coinductive.yfinance4s.models

/** Comprehensive holders data for a security.
  *
  * @param majorHolders
  *   Breakdown of ownership by category
  * @param institutionalHolders
  *   Top institutional investors
  * @param mutualFundHolders
  *   Top mutual fund investors
  * @param insiderTransactions
  *   Recent insider transactions
  * @param insiderRoster
  *   Current insider positions
  */
final case class HoldersData(
    majorHolders: Option[MajorHolders],
    institutionalHolders: List[InstitutionalHolder],
    mutualFundHolders: List[MutualFundHolder],
    insiderTransactions: List[InsiderTransaction],
    insiderRoster: List[InsiderRosterEntry]
) {

  /** True if any holders data is available */
  def nonEmpty: Boolean =
    majorHolders.isDefined ||
      institutionalHolders.nonEmpty ||
      mutualFundHolders.nonEmpty ||
      insiderTransactions.nonEmpty ||
      insiderRoster.nonEmpty

  /** True if no holders data is available */
  def isEmpty: Boolean = !nonEmpty

  /** Total number of institutional + mutual fund holders listed */
  def totalInstitutionalCount: Int =
    institutionalHolders.size + mutualFundHolders.size

  /** Combined percentage held by top institutional holders */
  def topInstitutionalPercentage: Double =
    institutionalHolders.map(_.percentHeld).sum

  /** Combined percentage held by top mutual fund holders */
  def topMutualFundPercentage: Double =
    mutualFundHolders.map(_.percentHeld).sum

  /** Recent purchases by insiders */
  def insiderPurchases: List[InsiderTransaction] =
    insiderTransactions.filter(_.isPurchase)

  /** Recent sales by insiders */
  def insiderSales: List[InsiderTransaction] =
    insiderTransactions.filter(_.isSale)

  /** Net insider sentiment (positive = more buying, negative = more selling) */
  def netInsiderShares: Long =
    insiderTransactions.map(_.shares).sum
}

object HoldersData {

  /** Empty holders data */
  val empty: HoldersData = HoldersData(
    majorHolders = None,
    institutionalHolders = List.empty,
    mutualFundHolders = List.empty,
    insiderTransactions = List.empty,
    insiderRoster = List.empty
  )
}
