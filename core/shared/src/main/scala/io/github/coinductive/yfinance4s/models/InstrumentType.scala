package io.github.coinductive.yfinance4s.models

/** The kind of instrument a ticker represents, from Yahoo's chart `meta.instrumentType`.
  *
  * Yahoo's set is open-ended (new categories appear over time), so an unrecognised wire value is preserved verbatim as
  * [[InstrumentType.Other]] rather than dropped or rejected.
  */
sealed trait InstrumentType extends Product with Serializable {

  /** The raw Yahoo wire value (e.g. "EQUITY"). */
  def raw: String

  /** True for pooled fund instruments (ETFs and mutual funds), which distribute capital gains. */
  def isFund: Boolean =
    this == InstrumentType.Etf || this == InstrumentType.MutualFund
}

object InstrumentType {
  case object Equity extends InstrumentType { val raw = "EQUITY" }
  case object Etf extends InstrumentType { val raw = "ETF" }
  case object MutualFund extends InstrumentType { val raw = "MUTUALFUND" }
  case object Index extends InstrumentType { val raw = "INDEX" }
  case object Currency extends InstrumentType { val raw = "CURRENCY" }
  case object CryptoCurrency extends InstrumentType { val raw = "CRYPTOCURRENCY" }
  case object Future extends InstrumentType { val raw = "FUTURE" }

  /** Any instrument type Yahoo reports that is not one of the known constants above. */
  final case class Other(raw: String) extends InstrumentType

  private val known: List[InstrumentType] =
    List(Equity, Etf, MutualFund, Index, Currency, CryptoCurrency, Future)

  /** Resolves a raw Yahoo instrument-type string to a known constant (case-insensitively), or [[Other]]. */
  def fromString(value: String): InstrumentType =
    known.find(_.raw.equalsIgnoreCase(value)).getOrElse(Other(value))
}
