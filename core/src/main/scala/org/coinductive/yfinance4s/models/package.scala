package org.coinductive.yfinance4s

import cats.Show
import io.estatico.newtype.macros.newtype

package object models {
  @newtype final case class Ticker(value: String)

  object Ticker {
    implicit val show: Show[Ticker] = deriving
  }
}
