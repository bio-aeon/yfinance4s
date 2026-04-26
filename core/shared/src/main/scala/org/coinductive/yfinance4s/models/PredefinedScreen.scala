package org.coinductive.yfinance4s.models

import enumeratum.*

/** A named predefined screener query available on Yahoo Finance.
  *
  * These correspond to Yahoo's built-in screens accessible via the `/v1/finance/screener/predefined/saved` endpoint.
  *
  * @param screenId
  *   The Yahoo API identifier (e.g., `"day_gainers"`)
  */
sealed abstract class PredefinedScreen(val screenId: String) extends EnumEntry

object PredefinedScreen extends Enum[PredefinedScreen] {
  case object AggressiveSmallCaps extends PredefinedScreen("aggressive_small_caps")
  case object DayGainers extends PredefinedScreen("day_gainers")
  case object DayLosers extends PredefinedScreen("day_losers")
  case object GrowthTechnologyStocks extends PredefinedScreen("growth_technology_stocks")
  case object MostActives extends PredefinedScreen("most_actives")
  case object MostShortedStocks extends PredefinedScreen("most_shorted_stocks")
  case object SmallCapGainers extends PredefinedScreen("small_cap_gainers")
  case object UndervaluedGrowthStocks extends PredefinedScreen("undervalued_growth_stocks")
  case object UndervaluedLargeCaps extends PredefinedScreen("undervalued_large_caps")
  case object ConservativeForeignFunds extends PredefinedScreen("conservative_foreign_funds")
  case object HighYieldBond extends PredefinedScreen("high_yield_bond")
  case object PortfolioAnchors extends PredefinedScreen("portfolio_anchors")
  case object SolidLargeGrowthFunds extends PredefinedScreen("solid_large_growth_funds")
  case object SolidMidcapGrowthFunds extends PredefinedScreen("solid_midcap_growth_funds")
  case object TopMutualFunds extends PredefinedScreen("top_mutual_funds")

  /** All predefined screens. */
  val values: IndexedSeq[PredefinedScreen] = findValues

  /** Equity-focused predefined screens. */
  val equityScreens: List[PredefinedScreen] = List(
    AggressiveSmallCaps,
    DayGainers,
    DayLosers,
    GrowthTechnologyStocks,
    MostActives,
    MostShortedStocks,
    SmallCapGainers,
    UndervaluedGrowthStocks,
    UndervaluedLargeCaps
  )

  /** Fund-focused predefined screens. */
  val fundScreens: List[PredefinedScreen] = List(
    ConservativeForeignFunds,
    HighYieldBond,
    PortfolioAnchors,
    SolidLargeGrowthFunds,
    SolidMidcapGrowthFunds,
    TopMutualFunds
  )

  /** Looks up a predefined screen by its Yahoo API identifier. */
  def fromScreenId(id: String): Option[PredefinedScreen] =
    values.find(_.screenId == id)
}
