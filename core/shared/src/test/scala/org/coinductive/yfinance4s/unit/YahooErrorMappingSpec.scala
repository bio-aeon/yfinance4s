package org.coinductive.yfinance4s.unit

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.YahooErrorMapping
import org.coinductive.yfinance4s.models.{Ticker, YFinanceError}
import org.coinductive.yfinance4s.models.internal.YahooErrorBody

class YahooErrorMappingSpec extends CatsEffectSuite {

  private val sampleTicker: Ticker = Ticker("FOO")

  test("raiseFor raises TickerNotFound when error code is 'Not Found'") {
    val error = YahooErrorBody("Not Found", "No data found")
    YahooErrorMapping.raiseFor[IO, Unit](sampleTicker, error).attempt.map {
      case Left(YFinanceError.TickerNotFound(t)) => assertEquals(t, sampleTicker)
      case other                                 => fail(s"expected TickerNotFound($sampleTicker), got $other")
    }
  }

  test("raiseFor raises RateLimited when error code is 'Too Many Requests'") {
    val error = YahooErrorBody("Too Many Requests", "slow down")
    YahooErrorMapping.raiseFor[IO, Unit](sampleTicker, error).attempt.map {
      case Left(YFinanceError.RateLimited(None)) => ()
      case other                                 => fail(s"expected RateLimited(None), got $other")
    }
  }

  test("raiseFor raises DataParseError carrying code and description for unknown error codes") {
    val error = YahooErrorBody("Internal Server Error", "boom")
    YahooErrorMapping.raiseFor[IO, Unit](sampleTicker, error).attempt.map {
      case Left(YFinanceError.DataParseError(msg, _)) =>
        assert(msg.contains("Internal Server Error"), s"expected code in message: $msg")
        assert(msg.contains("boom"), s"expected description in message: $msg")
      case other =>
        fail(s"expected DataParseError, got $other")
    }
  }

  test("raiseGeneric raises RateLimited for 'Too Many Requests' regardless of label") {
    val error = YahooErrorBody("Too Many Requests", "slow down")
    YahooErrorMapping.raiseGeneric[IO, Unit]("search", error).attempt.map {
      case Left(YFinanceError.RateLimited(None)) => ()
      case other                                 => fail(s"expected RateLimited(None), got $other")
    }
  }

  test("raiseGeneric folds 'Not Found' into DataParseError because there is no ticker context") {
    val error = YahooErrorBody("Not Found", "no results")
    YahooErrorMapping.raiseGeneric[IO, Unit]("calendar", error).attempt.map {
      case Left(YFinanceError.DataParseError(msg, _)) =>
        assert(msg.contains("calendar"), s"expected endpoint label in message: $msg")
        assert(msg.contains("Not Found"), s"expected code in message: $msg")
      case other =>
        fail(s"expected DataParseError, got $other")
    }
  }
}
