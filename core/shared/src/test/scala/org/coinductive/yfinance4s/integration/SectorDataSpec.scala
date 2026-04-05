package org.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.SectorKey

import scala.concurrent.duration.*

class SectorDataSpec extends CatsEffectSuite {

  val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  // --- getSectorData (comprehensive) ---

  test("returns comprehensive sector data for technology") {
    YFinanceClient.resource[IO](config).use { client =>
      client.sectors.getSectorData(SectorKey.Technology).map { result =>
        assert(result.isDefined, "Result should be defined for technology sector")
        val data = result.get

        assertEquals(data.key, SectorKey.Technology)
        assert(data.name.nonEmpty, "Sector name should not be empty")
        assert(data.nonEmpty, "Sector data should be non-empty")
      }
    }
  }

  // --- getSectorOverview ---

  test("returns sector overview with positive metrics for technology") {
    YFinanceClient.resource[IO](config).use { client =>
      client.sectors.getSectorOverview(SectorKey.Technology).map { result =>
        assert(result.isDefined, "Overview should be defined for technology sector")
        val overview = result.get

        overview.companiesCount.foreach { count =>
          assert(count > 0, s"Companies count should be positive: $count")
        }
        overview.marketCap.foreach { cap =>
          assert(cap > 0, s"Market cap should be positive: $cap")
        }
        overview.marketWeight.foreach { weight =>
          assert(weight > 0 && weight < 1, s"Market weight should be between 0 and 1: $weight")
        }
        overview.industriesCount.foreach { count =>
          assert(count > 0, s"Industries count should be positive: $count")
        }
      }
    }
  }

  // --- getTopETFs ---

  test("returns non-empty ETF list for technology sector") {
    YFinanceClient.resource[IO](config).use { client =>
      client.sectors.getTopETFs(SectorKey.Technology).map { etfs =>
        assert(etfs.nonEmpty, "Technology sector should have top ETFs")
        etfs.foreach { etf =>
          assert(etf.symbol.nonEmpty, "ETF symbol should not be empty")
        }
      }
    }
  }

  // --- getTopMutualFunds ---

  test("returns non-empty mutual fund list for technology sector") {
    YFinanceClient.resource[IO](config).use { client =>
      client.sectors.getTopMutualFunds(SectorKey.Technology).map { funds =>
        assert(funds.nonEmpty, "Technology sector should have top mutual funds")
        funds.foreach { fund =>
          assert(fund.symbol.nonEmpty, "Mutual fund symbol should not be empty")
        }
      }
    }
  }

  // --- getIndustries ---

  test("returns non-empty industries list for technology sector") {
    YFinanceClient.resource[IO](config).use { client =>
      client.sectors.getIndustries(SectorKey.Technology).map { industries =>
        assert(industries.nonEmpty, "Technology sector should have industries")
        industries.foreach { industry =>
          assert(industry.key.nonEmpty, "Industry key should not be empty")
          assert(industry.name.nonEmpty, "Industry name should not be empty")
        }
      }
    }
  }

  test("filters out 'All Industries' sentinel from industry list") {
    YFinanceClient.resource[IO](config).use { client =>
      client.sectors.getIndustries(SectorKey.Technology).map { industries =>
        assert(
          !industries.exists(_.name == "All Industries"),
          "Industries list should not contain 'All Industries' sentinel"
        )
      }
    }
  }

  // --- getTopCompanies ---

  test("returns non-empty top companies for technology sector") {
    YFinanceClient.resource[IO](config).use { client =>
      client.sectors.getTopCompanies(SectorKey.Technology).map { companies =>
        assert(companies.nonEmpty, "Technology sector should have top companies")
        companies.foreach { company =>
          assert(company.symbol.nonEmpty, "Company symbol should not be empty")
        }
      }
    }
  }

  // --- Multiple sectors ---

  test("returns sector data for healthcare sector") {
    YFinanceClient.resource[IO](config).use { client =>
      client.sectors.getSectorData(SectorKey.Healthcare).map { result =>
        assert(result.isDefined, "Result should be defined for healthcare sector")
        assert(result.get.nonEmpty, "Healthcare sector data should be non-empty")
      }
    }
  }

  test("returns sector data for energy sector") {
    YFinanceClient.resource[IO](config).use { client =>
      client.sectors.getSectorData(SectorKey.Energy).map { result =>
        assert(result.isDefined, "Result should be defined for energy sector")
        assert(result.get.nonEmpty, "Energy sector data should be non-empty")
      }
    }
  }
}
