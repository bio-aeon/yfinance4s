package org.coinductive.yfinance4s.models

import io.circe.{Encoder, Json}

/** A value used as an operand in a screener query filter — either a string or a number. */
sealed trait ScreenerValue {
  private[yfinance4s] def toJson: Json
}

object ScreenerValue {
  final case class StringVal(value: String) extends ScreenerValue {
    private[yfinance4s] def toJson: Json = Json.fromString(value)
  }

  final case class NumericVal(value: Double) extends ScreenerValue {
    private[yfinance4s] def toJson: Json =
      if (value == math.floor(value) && !value.isInfinite) Json.fromLong(value.toLong)
      else Json.fromDoubleOrNull(value)
  }

  def apply(s: String): ScreenerValue = StringVal(s)
  def apply(n: Double): ScreenerValue = NumericVal(n)
  def apply(n: Int): ScreenerValue = NumericVal(n.toDouble)
  def apply(n: Long): ScreenerValue = NumericVal(n.toDouble)

  implicit val encoder: Encoder[ScreenerValue] = Encoder.instance(_.toJson)
}

/** A recursive query tree for Yahoo Finance screener filters.
  *
  * Build queries using the [[EquityQuery]] or [[FundQuery]] companion builders, then pass them to
  * [[org.coinductive.yfinance4s.Screener Screener]] methods.
  *
  * {{{
  * val query = EquityQuery.and(
  *   EquityQuery.gt("percentchange", 3),
  *   EquityQuery.eq("region", "us"),
  *   EquityQuery.gte("intradaymarketcap", 2000000000L)
  * )
  * client.screener.screenEquities(query)
  * }}}
  */
sealed trait ScreenerQuery

object ScreenerQuery {
  final case class Eq(field: String, value: ScreenerValue) extends ScreenerQuery
  final case class Gt(field: String, value: Double) extends ScreenerQuery
  final case class Gte(field: String, value: Double) extends ScreenerQuery
  final case class Lt(field: String, value: Double) extends ScreenerQuery
  final case class Lte(field: String, value: Double) extends ScreenerQuery
  final case class Between(field: String, low: Double, high: Double) extends ScreenerQuery
  final case class IsIn(field: String, values: List[ScreenerValue]) extends ScreenerQuery {
    require(values.nonEmpty, "IS-IN requires at least one value")
  }
  final case class And(operands: List[ScreenerQuery]) extends ScreenerQuery {
    require(operands.size >= 2, "AND requires at least 2 operands")
  }
  final case class Or(operands: List[ScreenerQuery]) extends ScreenerQuery {
    require(operands.size >= 2, "OR requires at least 2 operands")
  }

  implicit val encoder: Encoder[ScreenerQuery] = Encoder.instance(encodeQuery)

  private def encodeQuery(q: ScreenerQuery): Json = q match {
    case Eq(field, value) =>
      Json.obj("operator" -> Json.fromString("EQ"), "operands" -> Json.arr(Json.fromString(field), value.toJson))

    case Gt(field, value) =>
      comparisonJson("GT", field, value)

    case Gte(field, value) =>
      comparisonJson("GTE", field, value)

    case Lt(field, value) =>
      comparisonJson("LT", field, value)

    case Lte(field, value) =>
      comparisonJson("LTE", field, value)

    case Between(field, low, high) =>
      Json.obj(
        "operator" -> Json.fromString("BTWN"),
        "operands" -> Json.arr(Json.fromString(field), numericJson(low), numericJson(high))
      )

    case IsIn(field, values) =>
      // IS-IN expands to OR of EQ queries per Yahoo API wire format
      val eqOps = values.map(v => encodeQuery(Eq(field, v)))
      Json.obj("operator" -> Json.fromString("OR"), "operands" -> Json.arr(eqOps *))

    case And(operands) =>
      Json.obj("operator" -> Json.fromString("AND"), "operands" -> Json.arr(operands.map(encodeQuery) *))

    case Or(operands) =>
      Json.obj("operator" -> Json.fromString("OR"), "operands" -> Json.arr(operands.map(encodeQuery) *))
  }

  private def comparisonJson(op: String, field: String, value: Double): Json =
    Json.obj("operator" -> Json.fromString(op), "operands" -> Json.arr(Json.fromString(field), numericJson(value)))

  private def numericJson(value: Double): Json =
    if (value == math.floor(value) && !value.isInfinite) Json.fromLong(value.toLong)
    else Json.fromDoubleOrNull(value)
}

/** Builder for equity (stock) screener queries. Queries built with this object will be sent with `quoteType =
  * "EQUITY"`.
  *
  * {{{
  * val q = EquityQuery.and(
  *   EquityQuery.gt("percentchange", 3),
  *   EquityQuery.eq("region", "us")
  * )
  * }}}
  */
object EquityQuery {
  def eq(field: String, value: String): ScreenerQuery = ScreenerQuery.Eq(field, ScreenerValue(value))
  def eq(field: String, value: Double): ScreenerQuery = ScreenerQuery.Eq(field, ScreenerValue(value))
  def eq(field: String, value: Int): ScreenerQuery = ScreenerQuery.Eq(field, ScreenerValue(value))

  def gt(field: String, value: Double): ScreenerQuery = ScreenerQuery.Gt(field, value)
  def gt(field: String, value: Int): ScreenerQuery = ScreenerQuery.Gt(field, value.toDouble)
  def gt(field: String, value: Long): ScreenerQuery = ScreenerQuery.Gt(field, value.toDouble)

  def gte(field: String, value: Double): ScreenerQuery = ScreenerQuery.Gte(field, value)
  def gte(field: String, value: Int): ScreenerQuery = ScreenerQuery.Gte(field, value.toDouble)
  def gte(field: String, value: Long): ScreenerQuery = ScreenerQuery.Gte(field, value.toDouble)

  def lt(field: String, value: Double): ScreenerQuery = ScreenerQuery.Lt(field, value)
  def lt(field: String, value: Int): ScreenerQuery = ScreenerQuery.Lt(field, value.toDouble)
  def lt(field: String, value: Long): ScreenerQuery = ScreenerQuery.Lt(field, value.toDouble)

  def lte(field: String, value: Double): ScreenerQuery = ScreenerQuery.Lte(field, value)
  def lte(field: String, value: Int): ScreenerQuery = ScreenerQuery.Lte(field, value.toDouble)
  def lte(field: String, value: Long): ScreenerQuery = ScreenerQuery.Lte(field, value.toDouble)

  def between(field: String, low: Double, high: Double): ScreenerQuery = ScreenerQuery.Between(field, low, high)
  def between(field: String, low: Long, high: Long): ScreenerQuery =
    ScreenerQuery.Between(field, low.toDouble, high.toDouble)

  def isIn(field: String, values: Seq[ScreenerValue]): ScreenerQuery = ScreenerQuery.IsIn(field, values.toList)

  def and(operands: ScreenerQuery*): ScreenerQuery = ScreenerQuery.And(operands.toList)
  def or(operands: ScreenerQuery*): ScreenerQuery = ScreenerQuery.Or(operands.toList)
}

/** Builder for mutual fund screener queries. Queries built with this object will be sent with `quoteType =
  * "MUTUALFUND"`.
  *
  * {{{
  * val q = FundQuery.and(
  *   FundQuery.eq("categoryname", "Large Growth"),
  *   FundQuery.isIn("performanceratingoverall", Seq(ScreenerValue(4), ScreenerValue(5)))
  * )
  * }}}
  */
object FundQuery {
  def eq(field: String, value: String): ScreenerQuery = ScreenerQuery.Eq(field, ScreenerValue(value))
  def eq(field: String, value: Double): ScreenerQuery = ScreenerQuery.Eq(field, ScreenerValue(value))
  def eq(field: String, value: Int): ScreenerQuery = ScreenerQuery.Eq(field, ScreenerValue(value))

  def gt(field: String, value: Double): ScreenerQuery = ScreenerQuery.Gt(field, value)
  def gt(field: String, value: Int): ScreenerQuery = ScreenerQuery.Gt(field, value.toDouble)
  def gt(field: String, value: Long): ScreenerQuery = ScreenerQuery.Gt(field, value.toDouble)

  def gte(field: String, value: Double): ScreenerQuery = ScreenerQuery.Gte(field, value)
  def gte(field: String, value: Int): ScreenerQuery = ScreenerQuery.Gte(field, value.toDouble)
  def gte(field: String, value: Long): ScreenerQuery = ScreenerQuery.Gte(field, value.toDouble)

  def lt(field: String, value: Double): ScreenerQuery = ScreenerQuery.Lt(field, value)
  def lt(field: String, value: Int): ScreenerQuery = ScreenerQuery.Lt(field, value.toDouble)
  def lt(field: String, value: Long): ScreenerQuery = ScreenerQuery.Lt(field, value.toDouble)

  def lte(field: String, value: Double): ScreenerQuery = ScreenerQuery.Lte(field, value)
  def lte(field: String, value: Int): ScreenerQuery = ScreenerQuery.Lte(field, value.toDouble)
  def lte(field: String, value: Long): ScreenerQuery = ScreenerQuery.Lte(field, value.toDouble)

  def between(field: String, low: Double, high: Double): ScreenerQuery = ScreenerQuery.Between(field, low, high)
  def between(field: String, low: Long, high: Long): ScreenerQuery =
    ScreenerQuery.Between(field, low.toDouble, high.toDouble)

  def isIn(field: String, values: Seq[ScreenerValue]): ScreenerQuery = ScreenerQuery.IsIn(field, values.toList)

  def and(operands: ScreenerQuery*): ScreenerQuery = ScreenerQuery.And(operands.toList)
  def or(operands: ScreenerQuery*): ScreenerQuery = ScreenerQuery.Or(operands.toList)
}
