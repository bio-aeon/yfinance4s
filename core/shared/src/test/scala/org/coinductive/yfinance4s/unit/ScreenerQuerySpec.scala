package org.coinductive.yfinance4s.unit

import io.circe.syntax.*
import munit.FunSuite
import org.coinductive.yfinance4s.models.*

class ScreenerQuerySpec extends FunSuite {

  // --- ScreenerValue tests ---

  test("encodes string values as JSON strings") {
    val v = ScreenerValue("us")
    assertEquals(v.toJson.asString, Some("us"))
  }

  test("encodes whole numbers without decimal points") {
    val v = ScreenerValue(3)
    assertEquals(v.toJson.asNumber.flatMap(_.toLong), Some(3L))
  }

  test("encodes fractional numbers with decimal points") {
    val v = ScreenerValue(3.5)
    assertEquals(v.toJson.asNumber.map(_.toDouble), Some(3.5))
  }

  test("encodes large numbers without precision loss") {
    val v = ScreenerValue(2000000000L)
    assertEquals(v.toJson.asNumber.flatMap(_.toLong), Some(2000000000L))
  }

  // --- EquityQuery builder tests ---

  test("eq creates equality filter for string value") {
    val q = EquityQuery.eq("region", "us")
    assert(q.isInstanceOf[ScreenerQuery.Eq])
    val node = q.asInstanceOf[ScreenerQuery.Eq]
    assertEquals(node.field, "region")
    assertEquals(node.value, ScreenerValue("us"))
  }

  test("eq creates equality filter for numeric value") {
    val q = EquityQuery.eq("score", 42.5)
    val node = q.asInstanceOf[ScreenerQuery.Eq]
    assertEquals(node.value, ScreenerValue(42.5))
  }

  test("gt creates greater-than filter") {
    val q = EquityQuery.gt("percentchange", 3.0)
    assert(q.isInstanceOf[ScreenerQuery.Gt])
    val gt = q.asInstanceOf[ScreenerQuery.Gt]
    assertEquals(gt.field, "percentchange")
    assertEquals(gt.value, 3.0)
  }

  test("gt accepts Int values") {
    val q = EquityQuery.gt("dayvolume", 15000)
    assertEquals(q.asInstanceOf[ScreenerQuery.Gt].value, 15000.0)
  }

  test("between creates range filter") {
    val q = EquityQuery.between("peratio.lasttwelvemonths", 0.0, 20.0)
    val btwn = q.asInstanceOf[ScreenerQuery.Between]
    assertEquals(btwn.low, 0.0)
    assertEquals(btwn.high, 20.0)
  }

  test("isIn creates set membership filter") {
    val q = EquityQuery.isIn("exchange", Seq(ScreenerValue("NMS"), ScreenerValue("NYQ")))
    val isIn = q.asInstanceOf[ScreenerQuery.IsIn]
    assertEquals(isIn.values.size, 2)
  }

  test("and combines multiple filters") {
    val q = EquityQuery.and(EquityQuery.gt("x", 1), EquityQuery.lt("y", 2))
    assert(q.isInstanceOf[ScreenerQuery.And])
    assertEquals(q.asInstanceOf[ScreenerQuery.And].operands.size, 2)
  }

  test("or creates alternative filters") {
    val q = EquityQuery.or(EquityQuery.eq("a", "x"), EquityQuery.eq("b", "y"))
    assert(q.isInstanceOf[ScreenerQuery.Or])
  }

  // --- FundQuery builder tests ---

  test("FundQuery produces same query types as EquityQuery") {
    val q = FundQuery.eq("categoryname", "Large Growth")
    assert(q.isInstanceOf[ScreenerQuery.Eq])
    assertEquals(q.asInstanceOf[ScreenerQuery.Eq].field, "categoryname")
  }

  // --- Validation tests ---

  test("rejects AND with fewer than 2 operands") {
    intercept[IllegalArgumentException] {
      ScreenerQuery.And(List(ScreenerQuery.Gt("x", 1)))
    }
  }

  test("rejects OR with fewer than 2 operands") {
    intercept[IllegalArgumentException] {
      ScreenerQuery.Or(List(ScreenerQuery.Gt("x", 1)))
    }
  }

  test("rejects IS-IN with no values") {
    intercept[IllegalArgumentException] {
      ScreenerQuery.IsIn("x", List.empty)
    }
  }

  // --- JSON encoding tests ---

  test("equality filter encodes as EQ operator with field and value") {
    val q: ScreenerQuery = ScreenerQuery.Eq("region", ScreenerValue("us"))
    val json = q.asJson
    assertEquals(json.hcursor.downField("operator").as[String], Right("EQ"))
    val operands = json.hcursor.downField("operands").as[List[io.circe.Json]].toOption.get
    assertEquals(operands.size, 2)
    assertEquals(operands(0).asString, Some("region"))
    assertEquals(operands(1).asString, Some("us"))
  }

  test("greater-than filter encodes whole numbers without decimals") {
    val q: ScreenerQuery = ScreenerQuery.Gt("percentchange", 3.0)
    val json = q.asJson
    assertEquals(json.hcursor.downField("operator").as[String], Right("GT"))
    val operands = json.hcursor.downField("operands").as[List[io.circe.Json]].toOption.get
    assertEquals(operands(1).asNumber.flatMap(_.toLong), Some(3L))
  }

  test("between filter encodes field with low and high bounds") {
    val q: ScreenerQuery = ScreenerQuery.Between("pe", 0.0, 20.0)
    val json = q.asJson
    assertEquals(json.hcursor.downField("operator").as[String], Right("BTWN"))
    val operands = json.hcursor.downField("operands").as[List[io.circe.Json]].toOption.get
    assertEquals(operands.size, 3)
    assertEquals(operands(0).asString, Some("pe"))
  }

  test("set membership expands to OR of equality checks") {
    val q: ScreenerQuery = ScreenerQuery.IsIn("exchange", List(ScreenerValue("NMS"), ScreenerValue("NYQ")))
    val json = q.asJson
    assertEquals(json.hcursor.downField("operator").as[String], Right("OR"))
    val operands = json.hcursor.downField("operands").as[List[io.circe.Json]].toOption.get
    assertEquals(operands.size, 2)
    // Each sub-operand is an EQ query
    assertEquals(operands(0).hcursor.downField("operator").as[String], Right("EQ"))
    val eqOps = operands(0).hcursor.downField("operands").as[List[io.circe.Json]].toOption.get
    assertEquals(eqOps(0).asString, Some("exchange"))
    assertEquals(eqOps(1).asString, Some("NMS"))
  }

  test("AND encodes nested filters recursively") {
    val q: ScreenerQuery = ScreenerQuery.And(
      List(
        ScreenerQuery.Gt("percentchange", 3.0),
        ScreenerQuery.Eq("region", ScreenerValue("us"))
      )
    )
    val json = q.asJson
    assertEquals(json.hcursor.downField("operator").as[String], Right("AND"))
    val operands = json.hcursor.downField("operands").as[List[io.circe.Json]].toOption.get
    assertEquals(operands.size, 2)
    assertEquals(operands(0).hcursor.downField("operator").as[String], Right("GT"))
    assertEquals(operands(1).hcursor.downField("operator").as[String], Right("EQ"))
  }

  test("day_gainers-equivalent query encodes all 5 filters") {
    val q: ScreenerQuery = EquityQuery.and(
      EquityQuery.gt("percentchange", 3),
      EquityQuery.eq("region", "us"),
      EquityQuery.gte("intradaymarketcap", 2000000000L),
      EquityQuery.gte("intradayprice", 5),
      EquityQuery.gt("dayvolume", 15000)
    )
    val json = q.asJson
    assertEquals(json.hcursor.downField("operator").as[String], Right("AND"))
    val operands = json.hcursor.downField("operands").as[List[io.circe.Json]].toOption.get
    assertEquals(operands.size, 5)
  }
}
