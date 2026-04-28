package org.coinductive.yfinance4s.models.internal

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

/** Yahoo Finance's structured `error` envelope returned by the chart, options, holders, analyst, calendar, and similar
  * endpoints when a request fails. Yahoo emits this even on HTTP 200 OK responses, so the gateway must inspect the JSON
  * body - not the HTTP status - to surface ticker-level failures.
  */
private[yfinance4s] final case class YahooErrorBody(code: String, description: String)

private[yfinance4s] object YahooErrorBody {
  implicit val decoder: Decoder[YahooErrorBody] = deriveDecoder
}
