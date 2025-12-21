package org.coinductive.yfinance4s.integration

import munit.CatsEffectSuite
import cats.effect._
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.Ticker
import scala.concurrent.duration._

class YFinanceClientSpec extends CatsEffectSuite {

  val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  test("getStock should return correct symbol, longName and exchangeName for AAPL") {
    YFinanceClient.resource[IO](config).use { client =>
      val ticker = Ticker("AAPL")

      client.getStock(ticker).map { stockResultOpt =>
        assert(stockResultOpt.isDefined, "Stock result should be defined for AAPL")

        val stockResult = stockResultOpt.get
        assertEquals(stockResult.symbol, "AAPL")
        assert(stockResult.longName.contains("Apple"), s"Expected longName to contain 'Apple', got: ${stockResult.longName}")
        assert(stockResult.exchangeName.nonEmpty, "Exchange name should not be empty")
      }
    }
  }

}
