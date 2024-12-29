package org.coinductive

import cats.Show
import io.estatico.newtype.macros.newtype

package object yfinance4s {
  @newtype final case class Ticker(value: String)

  object Ticker {
    implicit val show: Show[Ticker] = deriving
  }

}
