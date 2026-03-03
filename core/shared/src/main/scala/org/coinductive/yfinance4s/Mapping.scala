package org.coinductive.yfinance4s

import java.time.{Instant, LocalDate, ZoneOffset, ZonedDateTime}

private[yfinance4s] object Mapping {

  def epochToLocalDate(epochSeconds: Long): LocalDate =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDate

  def epochToZonedDateTime(epochSeconds: Long): ZonedDateTime =
    ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC)
}
