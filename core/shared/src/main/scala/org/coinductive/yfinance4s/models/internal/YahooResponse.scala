package org.coinductive.yfinance4s.models.internal

import io.circe.Decoder

/** Generic envelope around Yahoo Finance's response shape. Every endpoint that ships an `error` field alongside its
  * payload (chart, options, holders, analyst, calendar visualization, ...) decodes through this type, so the dual
  * `(result, error)` invariant lives in one place rather than being duplicated across raw response models.
  *
  * The decoder reads `error` first; when present it returns [[Failure]], otherwise it decodes the same JSON object as
  * the success body `A`. Yahoo never populates both fields simultaneously, so the sum type is a faithful representation
  * of the API contract.
  */
private[yfinance4s] sealed trait YahooResponse[+A]

private[yfinance4s] object YahooResponse {

  final case class Success[+A](data: A) extends YahooResponse[A]

  final case class Failure(error: YahooErrorBody) extends YahooResponse[Nothing]

  implicit def decoder[A: Decoder]: Decoder[YahooResponse[A]] = Decoder.instance { c =>
    c.downField("error").as[Option[YahooErrorBody]].flatMap {
      case Some(err) => Right(Failure(err))
      case None      => c.as[A].map(Success(_))
    }
  }
}
