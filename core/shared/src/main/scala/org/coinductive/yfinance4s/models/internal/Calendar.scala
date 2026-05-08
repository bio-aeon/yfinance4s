package org.coinductive.yfinance4s.models.internal

import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, Json}

/** Decoded payload of `POST /v1/finance/visualization` for both market-wide and per-ticker earnings queries.
  *
  * Rows are positional JSON arrays aligned with the `columns` list by index. Lookup happens in the algebra via
  * [[columnIndex]] keyed off each column's stable `id`, never the (potentially duplicated) `label`.
  */
private[yfinance4s] final case class Calendar(
    columns: List[CalendarColumn],
    rows: List[CalendarRow]
) {

  /** Zero-based column index keyed by Yahoo's stable `id` (not `label`). */
  def columnIndex: Map[String, Int] =
    columns.zipWithIndex.map { case (c, i) => c.id -> i }.toMap
}

private[yfinance4s] object Calendar {

  implicit val decoder: Decoder[Calendar] = Decoder.instance { c =>
    val doc = c.downField("result").downArray.downField("documents").downArray
    for {
      columns <- doc.downField("columns").as[Option[List[CalendarColumn]]].map(_.getOrElse(Nil))
      rows <- doc.downField("rows").as[Option[List[CalendarRow]]].map(_.getOrElse(Nil))
    } yield Calendar(columns = columns, rows = rows)
  }
}

private[yfinance4s] final case class CalendarColumn(
    id: String,
    label: String,
    `type`: String
)

private[yfinance4s] object CalendarColumn {
  implicit val decoder: Decoder[CalendarColumn] = deriveDecoder
}

/** Raw row as a positional array of JSON values. Lookup is by column index computed from [[Calendar.columnIndex]].
  */
private[yfinance4s] final case class CalendarRow(values: List[Json]) {

  def stringAt(idx: Int): Option[String] =
    values.lift(idx).flatMap(_.asString)

  def longAt(idx: Int): Option[Long] =
    values.lift(idx).flatMap(j => j.asNumber.flatMap(_.toLong))

  def doubleAt(idx: Int): Option[Double] =
    values.lift(idx).flatMap(j => j.asNumber.map(_.toDouble))
}

private[yfinance4s] object CalendarRow {
  implicit val decoder: Decoder[CalendarRow] =
    Decoder[List[Json]].map(CalendarRow(_))
}
