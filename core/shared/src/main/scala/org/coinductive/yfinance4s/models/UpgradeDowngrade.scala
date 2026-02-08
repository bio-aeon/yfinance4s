package org.coinductive.yfinance4s.models

import java.time.LocalDate

/** Represents an analyst upgrade, downgrade, or initiation event.
  *
  * @param date
  *   The date of the grade change
  * @param firm
  *   The name of the analyst firm (e.g., "Morgan Stanley", "Goldman Sachs")
  * @param toGrade
  *   The new grade assigned (e.g., "Overweight", "Buy", "Neutral")
  * @param fromGrade
  *   The previous grade, if applicable (None for initiations)
  * @param action
  *   The type of action taken
  */
final case class UpgradeDowngrade(
    date: LocalDate,
    firm: String,
    toGrade: String,
    fromGrade: Option[String],
    action: UpgradeDowngradeAction
) {

  /** Whether this is a positive action (upgrade). */
  def isUpgrade: Boolean = action == UpgradeDowngradeAction.Upgrade

  /** Whether this is a negative action (downgrade). */
  def isDowngrade: Boolean = action == UpgradeDowngradeAction.Downgrade

  /** Whether this is a new coverage initiation. */
  def isInitiation: Boolean = action == UpgradeDowngradeAction.Initiation

  /** Whether this is a maintained/reiterated rating. */
  def isMaintained: Boolean =
    action == UpgradeDowngradeAction.Maintained || action == UpgradeDowngradeAction.Reiterated
}

object UpgradeDowngrade {

  /** Ordering by date, most recent first. */
  implicit val ordering: Ordering[UpgradeDowngrade] =
    Ordering.by[UpgradeDowngrade, LocalDate](_.date).reverse
}

/** The type of analyst action. */
sealed trait UpgradeDowngradeAction {
  def value: String
}

object UpgradeDowngradeAction {
  case object Upgrade extends UpgradeDowngradeAction { val value = "up" }
  case object Downgrade extends UpgradeDowngradeAction { val value = "down" }
  case object Maintained extends UpgradeDowngradeAction { val value = "main" }
  case object Reiterated extends UpgradeDowngradeAction { val value = "reit" }
  case object Initiation extends UpgradeDowngradeAction { val value = "init" }
  final case class Other(value: String) extends UpgradeDowngradeAction

  def fromString(s: String): UpgradeDowngradeAction = s.toLowerCase match {
    case "up"   => Upgrade
    case "down" => Downgrade
    case "main" => Maintained
    case "reit" => Reiterated
    case "init" => Initiation
    case other  => Other(other)
  }
}
