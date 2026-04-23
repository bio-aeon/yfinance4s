package org.coinductive.yfinance4s.unit

import io.circe.Json
import io.circe.parser.parse
import munit.FunSuite
import org.coinductive.yfinance4s.Calendars
import org.coinductive.yfinance4s.models.*

import java.time.LocalDate

class CalendarsBodySpec extends FunSuite {

  private val start: LocalDate = LocalDate.of(2026, 1, 1)
  private val end: LocalDate = LocalDate.of(2026, 1, 7)

  private def parseJson(raw: String): Json =
    parse(raw).getOrElse(fail(s"body is not valid JSON: $raw"))

  private def stringField(json: Json, field: String): String =
    json.hcursor.downField(field).as[String].getOrElse(fail(s"missing $field"))

  private def intField(json: Json, field: String): Int =
    json.hcursor.downField(field).as[Int].getOrElse(fail(s"missing $field"))

  private def flattenOperands(query: Json): List[Json] = {
    def loop(j: Json): List[Json] =
      j.hcursor.downField("operands").as[List[Json]] match {
        case Right(ops) => j :: ops.flatMap(loop)
        case Left(_)    => List(j)
      }
    loop(query)
  }

  private def findOperand(query: Json, operator: String, field: String): Option[Json] =
    flattenOperands(query).find { op =>
      val isOp = op.hcursor.downField("operator").as[String].exists(_ == operator)
      val hasField = op.hcursor.downField("operands").downArray.as[String].exists(_ == field)
      isOp && hasField
    }

  // --- Market-wide body ---

  test("market-wide body uses the sp_earnings entity type and market-cap sort") {
    val body = parseJson(Calendars.buildMarketWideBody(start, end, CalendarConfig.Default))
    assertEquals(stringField(body, "entityIdType"), "sp_earnings")
    assertEquals(stringField(body, "sortField"), "intradaymarketcap")
    assertEquals(stringField(body, "sortType"), "DESC")
  }

  test("market-wide body includes the requested date bounds as startdatetime operands") {
    val body = parseJson(Calendars.buildMarketWideBody(start, end, CalendarConfig.Default))
    val query = body.hcursor.downField("query").as[Json].getOrElse(fail("missing query"))

    val gte = findOperand(query, "GTE", "startdatetime")
      .getOrElse(fail(s"no GTE startdatetime operand in ${query.noSpaces}"))
    val lte = findOperand(query, "LTE", "startdatetime")
      .getOrElse(fail(s"no LTE startdatetime operand in ${query.noSpaces}"))

    assertEquals(gte.hcursor.downField("operands").downN(1).as[String].toOption, Some("2026-01-01"))
    assertEquals(lte.hcursor.downField("operands").downN(1).as[String].toOption, Some("2026-01-07"))
  }

  test("market-wide body restricts eventtype to EAD and ERA via an OR branch") {
    val body = parseJson(Calendars.buildMarketWideBody(start, end, CalendarConfig.Default))
    val query = body.hcursor.downField("query").as[Json].getOrElse(fail("missing query"))
    val ops = flattenOperands(query)

    val orBranch = ops
      .find { op =>
        val isOr = op.hcursor.downField("operator").as[String].exists(_ == "OR")
        isOr && op.hcursor
          .downField("operands")
          .downArray
          .downField("operands")
          .downN(0)
          .as[String]
          .toOption
          .contains("eventtype")
      }
      .getOrElse(fail(s"no OR eventtype branch in ${query.noSpaces}"))

    val eventTypeValues = orBranch.hcursor.downField("operands").as[List[Json]].getOrElse(Nil).flatMap { inner =>
      inner.hcursor.downField("operands").downN(1).as[String].toOption
    }
    assertEquals(eventTypeValues.toSet, Set("EAD", "ERA"))
  }

  test("market-wide body omits the intradaymarketcap operand when no market cap is configured") {
    val body = parseJson(Calendars.buildMarketWideBody(start, end, CalendarConfig.Default))
    val query = body.hcursor.downField("query").as[Json].getOrElse(fail("missing query"))
    assert(findOperand(query, "GTE", "intradaymarketcap").isEmpty)
  }

  test("market-wide body appends an intradaymarketcap GTE operand when the config requests it") {
    val config = CalendarConfig(marketCap = Some(10_000_000_000d))
    val body = parseJson(Calendars.buildMarketWideBody(start, end, config))
    val query = body.hcursor.downField("query").as[Json].getOrElse(fail("missing query"))

    val op = findOperand(query, "GTE", "intradaymarketcap")
      .getOrElse(fail(s"no GTE intradaymarketcap in ${query.noSpaces}"))
    assertEquals(op.hcursor.downField("operands").downN(1).as[Long].toOption, Some(10_000_000_000L))
  }

  test("market-wide body caps size at the Yahoo maximum when the config requests more") {
    val body = parseJson(
      Calendars.buildMarketWideBody(start, end, CalendarConfig.Default.copy(limit = CalendarConfig.MaxLimit))
    )
    assertEquals(intField(body, "size"), Calendars.MaxLimit)
  }

  test("market-wide body respects the configured offset") {
    val body = parseJson(Calendars.buildMarketWideBody(start, end, CalendarConfig.Default.copy(offset = 25)))
    assertEquals(intField(body, "offset"), 25)
  }

  // --- Per-ticker body ---

  test("per-ticker body uses the earnings entity type and startdatetime descending sort") {
    val body = parseJson(Calendars.buildPerTickerBody(Ticker("AAPL"), limit = 20, offset = 0))
    assertEquals(stringField(body, "entityIdType"), "earnings")
    assertEquals(stringField(body, "sortField"), "startdatetime")
    assertEquals(stringField(body, "sortType"), "DESC")
  }

  test("per-ticker body sends an EQ ticker operand carrying the exact ticker value") {
    val body = parseJson(Calendars.buildPerTickerBody(Ticker("AAPL"), limit = 12, offset = 0))
    val query = body.hcursor.downField("query").as[Json].getOrElse(fail("missing query"))
    assertEquals(query.hcursor.downField("operator").as[String].toOption, Some("EQ"))
    assertEquals(query.hcursor.downField("operands").downN(0).as[String].toOption, Some("ticker"))
    assertEquals(query.hcursor.downField("operands").downN(1).as[String].toOption, Some("AAPL"))
  }

  test("per-ticker body includes eventtype in includeFields but market-wide body does not") {
    val perTicker = parseJson(Calendars.buildPerTickerBody(Ticker("AAPL"), limit = 12, offset = 0))
    val marketWide = parseJson(Calendars.buildMarketWideBody(start, end, CalendarConfig.Default))

    val perTickerFields = perTicker.hcursor.downField("includeFields").as[List[String]].getOrElse(Nil)
    val marketWideFields = marketWide.hcursor.downField("includeFields").as[List[String]].getOrElse(Nil)

    assert(perTickerFields.contains("eventtype"), s"per-ticker missing eventtype: $perTickerFields")
    assert(!marketWideFields.contains("eventtype"), s"market-wide should not request eventtype: $marketWideFields")
  }

  test("per-ticker body caps size at the Yahoo maximum when caller requests more") {
    val body = parseJson(Calendars.buildPerTickerBody(Ticker("AAPL"), limit = 500, offset = 0))
    assertEquals(intField(body, "size"), Calendars.MaxLimit)
  }
}
