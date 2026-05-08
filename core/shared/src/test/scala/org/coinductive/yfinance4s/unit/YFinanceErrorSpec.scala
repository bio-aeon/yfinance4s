package org.coinductive.yfinance4s.unit

import munit.FunSuite
import org.coinductive.yfinance4s.models.YFinanceError

import scala.concurrent.duration.*

class YFinanceErrorSpec extends FunSuite {

  test("RateLimited message includes the retry-after duration in seconds when present") {
    val msg = YFinanceError.RateLimited(Some(30.seconds)).getMessage
    assert(msg.contains("Retry-After: 30s"), s"expected 'Retry-After: 30s' in: $msg")
  }

  test("RateLimited message omits the retry-after suffix when absent") {
    val msg = YFinanceError.RateLimited(None).getMessage
    assert(!msg.contains("Retry-After"), s"did not expect 'Retry-After' in: $msg")
  }
}
