package org.coinductive.yfinance4s.unit

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.YahooErrorMapping
import org.coinductive.yfinance4s.models.{Ticker, YFinanceError}
import org.coinductive.yfinance4s.models.internal.YahooErrorBody

class YahooErrorMappingSpec extends CatsEffectSuite {

  private val sampleTicker: Ticker = Ticker("FOO")

  test("raises TickerNotFound when error code is 'Not Found'") {
    val error = Some(YahooErrorBody("Not Found", "No data found"))
    YahooErrorMapping.raiseIfPresent[IO](sampleTicker, error).attempt.map {
      case Left(YFinanceError.TickerNotFound(t)) => assertEquals(t, sampleTicker)
      case other                                 => fail(s"expected TickerNotFound($sampleTicker), got $other")
    }
  }

  test("raises DataParseError carrying code and description for non-NotFound error codes") {
    val error = Some(YahooErrorBody("Internal Server Error", "boom"))
    YahooErrorMapping.raiseIfPresent[IO](sampleTicker, error).attempt.map {
      case Left(YFinanceError.DataParseError(msg, _)) =>
        assert(msg.contains("Internal Server Error"), s"expected code in message: $msg")
        assert(msg.contains("boom"), s"expected description in message: $msg")
      case other =>
        fail(s"expected DataParseError, got $other")
    }
  }

  test("succeeds without raising when no error envelope is present") {
    YahooErrorMapping.raiseIfPresent[IO](sampleTicker, None).assertEquals(())
  }
}
