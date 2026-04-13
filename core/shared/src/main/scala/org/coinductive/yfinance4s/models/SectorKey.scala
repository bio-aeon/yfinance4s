package org.coinductive.yfinance4s.models

import cats.Show

final case class SectorKey(value: String) extends AnyVal

object SectorKey {
  val BasicMaterials: SectorKey = SectorKey("basic-materials")
  val CommunicationServices: SectorKey = SectorKey("communication-services")
  val ConsumerCyclical: SectorKey = SectorKey("consumer-cyclical")
  val ConsumerDefensive: SectorKey = SectorKey("consumer-defensive")
  val Energy: SectorKey = SectorKey("energy")
  val FinancialServices: SectorKey = SectorKey("financial-services")
  val Healthcare: SectorKey = SectorKey("healthcare")
  val Industrials: SectorKey = SectorKey("industrials")
  val RealEstate: SectorKey = SectorKey("real-estate")
  val Technology: SectorKey = SectorKey("technology")
  val Utilities: SectorKey = SectorKey("utilities")

  /** All 11 GICS sector keys. */
  val all: List[SectorKey] = List(
    BasicMaterials,
    CommunicationServices,
    ConsumerCyclical,
    ConsumerDefensive,
    Energy,
    FinancialServices,
    Healthcare,
    Industrials,
    RealEstate,
    Technology,
    Utilities
  )

  implicit val show: Show[SectorKey] = Show.show(_.value)
}
