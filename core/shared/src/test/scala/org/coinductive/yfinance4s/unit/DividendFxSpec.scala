package org.coinductive.yfinance4s.unit

import cats.data.NonEmptyList
import munit.FunSuite
import org.coinductive.yfinance4s.{DividendFxConversion, FxRateSource, PriceRepair}
import org.coinductive.yfinance4s.DividendFxConversion.{FxLeg, FxPlan}
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.internal.*

import java.time.{Instant, ZoneOffset, ZonedDateTime}

class DividendFxSpec extends FunSuite {

  private type Attempt[A] = Either[Throwable, A]

  private val baseEpoch = 1704067200L // 2024-01-01T00:00:00Z
  private val daySeconds = 86400L

  private def epochAt(i: Int): Long = baseEpoch + i * daySeconds

  private def utc(epochSeconds: Long): ZonedDateTime =
    ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC)

  private def metaRaw(currency: String): ChartMetaRaw = ChartMetaRaw(
    currency = currency,
    symbol = "TEST",
    exchangeName = "NMS",
    fullExchangeName = None,
    instrumentType = "EQUITY",
    firstTradeDate = None,
    regularMarketTime = None,
    gmtoffset = 0L,
    timezone = "UTC",
    exchangeTimezoneName = "UTC",
    regularMarketPrice = None,
    chartPreviousClose = None,
    priceHint = None,
    currentTradingPeriod = None,
    dataGranularity = "1d",
    range = "1mo",
    validRanges = None,
    hasPrePostMarketData = None
  )

  /** Instrument data whose dividends carry the given (bar index, currency label) pairs. */
  private def dataWith(currency: String, dividends: (Int, Option[String])*): InstrumentData = {
    val prices = List.fill(4)(100.0)
    InstrumentData(
      meta = metaRaw(currency),
      timestamp = prices.indices.map(epochAt).toList,
      indicators = Indicators(
        quote = NonEmptyList.one(
          Quote(close = prices, open = prices, volume = List.fill(4)(1000L), high = prices, low = prices)
        ),
        adjclose = NonEmptyList.one(AdjClose(prices))
      ),
      events = Some(
        Events(
          dividends = Some(dividends.map { case (i, label) =>
            epochAt(i).toString -> DividendEventRaw(amount = 1.0, date = epochAt(i), currency = label)
          }.toMap),
          splits = None
        )
      )
    )
  }

  /** A daily FX chart of flat bars. */
  private def fxChart(closes: Vector[Double]): Chart = {
    val values = closes.toList
    Chart(
      List(
        InstrumentData(
          meta = metaRaw("USD"),
          timestamp = closes.indices.map(epochAt).toList,
          indicators = Indicators(
            quote = NonEmptyList.one(
              Quote(close = values, open = values, volume = List.fill(closes.size)(1000L), high = values, low = values)
            ),
            adjclose = NonEmptyList.one(AdjClose(values))
          ),
          events = None
        )
      )
    )
  }

  private def bar(index: Int, dividend: Double, dividendCurrency: Option[String]): PriceRepair.Bar =
    PriceRepair.Bar(
      datetime = utc(epochAt(index)),
      open = 100.0,
      high = 100.0,
      low = 100.0,
      close = 100.0,
      adjClose = 100.0,
      volume = 1000L,
      dividend = dividend,
      splitRatio = 0.0,
      repaired = false,
      dividendCurrency = dividendCurrency
    )

  private def sourceOf(rates: (String, Option[Double])*): FxRateSource[Attempt] = {
    val table = rates.toMap
    new FxRateSource[Attempt] {
      def latestRate(ticker: Ticker): Attempt[Option[Double]] =
        table.get(ticker.value) match {
          case Some(rate) => Right(rate)
          case None       => Left(YFinanceError.TickerNotFound(ticker))
        }
    }
  }

  private val enabled = PriceRepairConfig.resolve(PriceRepairConfig.Enabled)

  private def plansFor(data: InstrumentData, cfg: PriceRepairConfig.Custom = enabled): List[FxPlan] =
    DividendFxConversion.requiredPlans(data, cfg)

  test("plans a single leg for dollar dividends") {
    val plans = plansFor(dataWith("GBP", 1 -> Some("USD")))
    assertEquals(plans, List(FxPlan("USD", List(FxLeg(Ticker("GBP=X"), inverted = false)))))
  }

  test("plans an inverted dollar leg for a dollar-priced chart") {
    val plans = plansFor(dataWith("USD", 1 -> Some("EUR")))
    assertEquals(plans, List(FxPlan("EUR", List(FxLeg(Ticker("EUR=X"), inverted = true)))))
  }

  test("plans a direct cross pair between major currencies") {
    val plans = plansFor(dataWith("GBP", 1 -> Some("EUR")))
    assertEquals(plans, List(FxPlan("EUR", List(FxLeg(Ticker("EURGBP=X"), inverted = false)))))
  }

  test("routes an exotic currency through dollars in two legs") {
    val plans = plansFor(dataWith("GBP", 1 -> Some("VND")))
    assertEquals(
      plans,
      List(FxPlan("VND", List(FxLeg(Ticker("VND=X"), inverted = true), FxLeg(Ticker("GBP=X"), inverted = false))))
    )
  }

  test("plans each mismatched currency once") {
    val plans = plansFor(dataWith("GBP", 0 -> Some("VND"), 1 -> Some("VND"), 2 -> Some("GBP")))
    assertEquals(plans.map(_.dividendCurrency), List("VND"))
  }

  test("plans nothing when conversion is disabled") {
    val plans = plansFor(dataWith("GBP", 1 -> Some("USD")), enabled.copy(convertDividendFx = false))
    assertEquals(plans, Nil)
  }

  test("plans nothing when every dividend matches the price currency") {
    val plans = plansFor(dataWith("GBP", 0 -> Some("GBP"), 1 -> None))
    assertEquals(plans, Nil)
  }

  test("plans nothing into a subunit price currency") {
    val plans = plansFor(dataWith("GBp", 1 -> Some("USD")), enabled.copy(standardiseCurrency = false))
    assertEquals(plans, Nil)
  }

  test("treats a subunit-labelled dividend as its major currency when planning") {
    val plans = plansFor(dataWith("USD", 1 -> Some("GBp")))
    assertEquals(plans, List(FxPlan("GBP", List(FxLeg(Ticker("GBP=X"), inverted = true)))))
  }

  test("plans nothing for a subunit-labelled dividend when standardisation is off") {
    val plans = plansFor(dataWith("USD", 1 -> Some("GBp")), enabled.copy(standardiseCurrency = false))
    assertEquals(plans, Nil)
  }

  test("resolves a two-leg plan as the product of its legs") {
    val plans = plansFor(dataWith("GBP", 1 -> Some("VND")))
    val rates = DividendFxConversion.resolveRates(plans, sourceOf("VND=X" -> Some(25000.0), "GBP=X" -> Some(0.8)))

    val resolved = rates.getOrElse(fail("resolution must not fail"))
    val expected = (1.0 / 25000.0) * 0.8
    assert(math.abs(resolved("VND") - expected) < 1e-12, s"expected ~$expected, got ${resolved("VND")}")
  }

  test("fetches each distinct leg ticker once") {
    var fetches = List.empty[String]
    val counting = new FxRateSource[Attempt] {
      def latestRate(ticker: Ticker): Attempt[Option[Double]] = {
        fetches = fetches :+ ticker.value
        Right(Some(2.0))
      }
    }
    val plans = plansFor(dataWith("GBP", 0 -> Some("VND"), 1 -> Some("THB")))
    DividendFxConversion.resolveRates(plans, counting).getOrElse(fail("resolution must not fail"))

    assertEquals(fetches.count(_ == "GBP=X"), 1, s"the shared leg must be fetched once, saw $fetches")
    assertEquals(fetches.toSet, Set("VND=X", "THB=X", "GBP=X"))
  }

  test("drops a currency whose rate fetch fails") {
    val plans = plansFor(dataWith("USD", 0 -> Some("EUR"), 1 -> Some("JPY")))
    val rates = DividendFxConversion
      .resolveRates(plans, sourceOf("JPY=X" -> Some(150.0)))
      .getOrElse(fail("a failed leg must not propagate"))

    assert(!rates.contains("EUR"), "the unfetchable currency drops out")
    assert(rates.contains("JPY"), "the other currency still resolves")
  }

  test("drops a currency whose rate is unusable") {
    val plans = plansFor(dataWith("USD", 1 -> Some("EUR")))
    val rates = DividendFxConversion
      .resolveRates(plans, sourceOf("EUR=X" -> Some(0.0)))
      .getOrElse(fail("resolution must not fail"))

    assertEquals(rates, Map.empty[String, Double])
  }

  test("takes the newest finite positive close as the rate") {
    val chart = fxChart(Vector(1.20, 1.25, 1.30, 0.0, Double.NaN))
    assertEquals(DividendFxConversion.lastUsableClose(Ticker("GBP=X"), chart), Some(1.30))
  }

  test("repairs a corrupt final rate bar before use") {
    val chart = fxChart(Vector(1.30, 1.30, 1.30, 1.30, 130.0))
    val rate = DividendFxConversion.lastUsableClose(Ticker("GBP=X"), chart).getOrElse(fail("expected a rate"))

    assert(math.abs(rate - 1.30) < 0.001, s"the corrupt final bar should be repaired, got $rate")
  }

  test("converts mismatched dividends and relabels them") {
    val bars = Vector(
      bar(0, dividend = 1.0, dividendCurrency = Some("USD")),
      bar(1, dividend = 2.0, dividendCurrency = None),
      bar(2, dividend = 3.0, dividendCurrency = Some("GBP"))
    )
    val result = DividendFxConversion.applyRates(bars, "GBP", Map("USD" -> 0.8))

    assert(math.abs(result(0).dividend - 0.8) < 1e-9, s"expected 0.8, got ${result(0).dividend}")
    assertEquals(result(0).dividendCurrency, Some("GBP"))
    assertEquals(result(1), bars(1), "an unlabelled dividend is already in the price currency")
    assertEquals(result(2), bars(2), "a matching label needs no conversion")
    assert(result.forall(!_.repaired), "conversion is not an error repair")
  }

  test("leaves a dividend unconverted when its rate is missing") {
    val bars = Vector(bar(0, dividend = 1.0, dividendCurrency = Some("USD")))
    val result = DividendFxConversion.applyRates(bars, "GBP", Map.empty)

    assertEquals(result, bars, "an unresolved dividend stays visibly foreign")
  }
}
