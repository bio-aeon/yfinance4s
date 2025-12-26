package org.coinductive.yfinance4s

import cats.Show

package object models {
  final case class Ticker(value: String) extends AnyVal

  object Ticker {
    implicit val show: Show[Ticker] = Show.show(_.value)
  }
}
