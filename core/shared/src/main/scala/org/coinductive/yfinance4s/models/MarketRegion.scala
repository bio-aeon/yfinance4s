package org.coinductive.yfinance4s.models

import cats.Show

/** A Yahoo Finance market region (e.g., `MarketRegion("US")`, `MarketRegion("JP")`).
  *
  * Yahoo accepts uppercase ISO-3166 alpha-2 codes for ~20 regions. The set isn't standardized - common values include
  * US, GB, CA, DE, FR, IT, ES, AU, JP, HK, IN, BR, MX. No constants are defined on the companion: a curated subset
  * would imply "these are blessed, others aren't", which is false - any ISO code Yahoo accepts works identically.
  * Construct instances directly: `MarketRegion("GB")`.
  *
  * Codes are case-sensitive on Yahoo's side (`"us"` is rejected); the newtype does not normalize.
  */
final case class MarketRegion(code: String) extends AnyVal

object MarketRegion {
  implicit val show: Show[MarketRegion] = Show.show(_.code)
}
