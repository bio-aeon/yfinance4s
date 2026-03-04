package org.coinductive.yfinance4s.models

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

private[yfinance4s] final case class YFinanceSearchResult(
    count: Int,
    quotes: List[SearchQuoteRaw],
    news: List[SearchNewsRaw],
    lists: Option[List[SearchListRaw]]
)

private[yfinance4s] object YFinanceSearchResult {
  implicit val decoder: Decoder[YFinanceSearchResult] = deriveDecoder
}

private[yfinance4s] final case class SearchQuoteRaw(
    symbol: Option[String],
    shortname: Option[String],
    longname: Option[String],
    quoteType: Option[String],
    exchange: Option[String],
    exchDisp: Option[String],
    sector: Option[String],
    sectorDisp: Option[String],
    industry: Option[String],
    industryDisp: Option[String],
    score: Option[Double],
    typeDisp: Option[String],
    isYahooFinance: Option[Boolean]
)

private[yfinance4s] object SearchQuoteRaw {
  implicit val decoder: Decoder[SearchQuoteRaw] = deriveDecoder
}

private[yfinance4s] final case class SearchNewsThumbnailResolution(
    url: String,
    width: Int,
    height: Int,
    tag: String
)

private[yfinance4s] object SearchNewsThumbnailResolution {
  implicit val decoder: Decoder[SearchNewsThumbnailResolution] = deriveDecoder
}

private[yfinance4s] final case class SearchNewsThumbnailRaw(
    resolutions: List[SearchNewsThumbnailResolution]
)

private[yfinance4s] object SearchNewsThumbnailRaw {
  implicit val decoder: Decoder[SearchNewsThumbnailRaw] = deriveDecoder
}

private[yfinance4s] final case class SearchNewsRaw(
    uuid: String,
    title: String,
    publisher: String,
    link: String,
    providerPublishTime: Long,
    `type`: Option[String],
    thumbnail: Option[SearchNewsThumbnailRaw],
    relatedTickers: Option[List[String]]
)

private[yfinance4s] object SearchNewsRaw {
  implicit val decoder: Decoder[SearchNewsRaw] = deriveDecoder
}

private[yfinance4s] final case class SearchListRaw(
    slug: Option[String],
    title: Option[String],
    description: Option[String],
    pfId: Option[String],
    canonicalName: Option[String]
)

private[yfinance4s] object SearchListRaw {
  implicit val decoder: Decoder[SearchListRaw] = deriveDecoder
}
