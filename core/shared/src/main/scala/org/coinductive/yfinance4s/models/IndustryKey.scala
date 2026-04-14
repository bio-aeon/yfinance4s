package org.coinductive.yfinance4s.models

import cats.Show

/** A Yahoo Finance industry key (e.g., `IndustryKey("semiconductors")`).
  *
  * Yahoo exposes ~150+ industry keys across 11 sectors; discover them dynamically via [[Sectors#getIndustries]], which
  * returns [[SectorIndustry]] entries whose `.key` values can be wrapped in `IndustryKey`.
  */
final case class IndustryKey(value: String) extends AnyVal

object IndustryKey {
  implicit val show: Show[IndustryKey] = Show.show(_.value)
}
