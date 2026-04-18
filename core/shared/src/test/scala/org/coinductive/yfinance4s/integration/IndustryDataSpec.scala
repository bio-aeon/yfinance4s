package org.coinductive.yfinance4s.integration

import cats.effect.IO
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{YFinanceClient, YFinanceClientConfig}
import org.coinductive.yfinance4s.models.{IndustryKey, SectorKey}

import scala.concurrent.duration.*

class IndustryDataSpec extends CatsEffectSuite {

  val config: YFinanceClientConfig = YFinanceClientConfig(
    connectTimeout = 10.seconds,
    readTimeout = 30.seconds,
    retries = 3
  )

  // --- getIndustryData ---

  test("returns comprehensive industry data for semiconductors") {
    YFinanceClient.resource[IO](config).use { client =>
      client.industries.getIndustryData(IndustryKey("semiconductors")).map { data =>
        assert(data.name.nonEmpty, "Industry name should not be empty")
        assert(data.nonEmpty, "Industry data should be non-empty")
        assertEquals(data.sectorKey, "technology")
      }
    }
  }

  // --- getIndustryOverview ---

  test("returns industry overview with positive metrics for semiconductors") {
    YFinanceClient.resource[IO](config).use { client =>
      client.industries.getIndustryOverview(IndustryKey("semiconductors")).map { result =>
        assert(result.isDefined, "Overview should be defined for semiconductors")
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
        overview.employeeCount.foreach { count =>
          assert(count > 0, s"Employee count should be positive: $count")
        }
      }
    }
  }

  // --- getSectorInfo ---

  test("returns sector info pointing to technology for semiconductors") {
    YFinanceClient.resource[IO](config).use { client =>
      client.industries.getSectorInfo(IndustryKey("semiconductors")).map { info =>
        assertEquals(info.sectorKey, "technology")
        assertEquals(info.toSectorKey, SectorKey.Technology)
      }
    }
  }

  // --- getTopCompanies ---

  test("returns non-empty top companies for semiconductors") {
    YFinanceClient.resource[IO](config).use { client =>
      client.industries.getTopCompanies(IndustryKey("semiconductors")).map { companies =>
        assert(companies.nonEmpty, "Semiconductors industry should have top companies")
        companies.foreach { company =>
          assert(company.symbol.nonEmpty, "Company symbol should not be empty")
        }
      }
    }
  }

  // --- getTopPerformingCompanies ---

  test("returns non-empty top performing companies for semiconductors") {
    YFinanceClient.resource[IO](config).use { client =>
      client.industries.getTopPerformingCompanies(IndustryKey("semiconductors")).map { performers =>
        assert(performers.nonEmpty, "Semiconductors industry should have top performers")
        performers.foreach { p =>
          assert(p.symbol.nonEmpty, "Performer symbol should not be empty")
        }
      }
    }
  }

  test("top performing companies are sorted by ytd return descending") {
    YFinanceClient.resource[IO](config).use { client =>
      client.industries.getTopPerformingCompanies(IndustryKey("semiconductors")).map { performers =>
        assertEquals(performers, performers.sorted)
      }
    }
  }

  // --- getTopGrowthCompanies ---

  test("returns non-empty top growth companies for semiconductors") {
    YFinanceClient.resource[IO](config).use { client =>
      client.industries.getTopGrowthCompanies(IndustryKey("semiconductors")).map { growers =>
        assert(growers.nonEmpty, "Semiconductors industry should have top growth companies")
        growers.foreach { g =>
          assert(g.symbol.nonEmpty, "Grower symbol should not be empty")
        }
      }
    }
  }

  test("top growth companies are sorted by growth estimate descending") {
    YFinanceClient.resource[IO](config).use { client =>
      client.industries.getTopGrowthCompanies(IndustryKey("semiconductors")).map { growers =>
        assertEquals(growers, growers.sorted)
      }
    }
  }

  // --- Cross-sector coverage ---

  test("returns industry data for software-infrastructure (technology sector)") {
    YFinanceClient.resource[IO](config).use { client =>
      client.industries.getIndustryData(IndustryKey("software-infrastructure")).map { data =>
        assertEquals(data.sectorKey, "technology")
        assert(data.nonEmpty)
      }
    }
  }

  test("returns industry data for biotechnology (healthcare sector)") {
    YFinanceClient.resource[IO](config).use { client =>
      client.industries.getIndustryData(IndustryKey("biotechnology")).map { data =>
        assertEquals(data.sectorKey, "healthcare")
        assert(data.nonEmpty)
      }
    }
  }

  test("returns industry data for banks-diversified (financial-services sector)") {
    YFinanceClient.resource[IO](config).use { client =>
      client.industries.getIndustryData(IndustryKey("banks-diversified")).map { data =>
        assertEquals(data.sectorKey, "financial-services")
        assert(data.nonEmpty)
      }
    }
  }

  // --- End-to-end discovery flow ---

  test("discovers industry key via sector listing and fetches it") {
    YFinanceClient.resource[IO](config).use { client =>
      for {
        industries <- client.sectors.getIndustries(SectorKey.Technology)
        _ = assert(industries.nonEmpty, "Technology sector should have industries")
        firstIndustry = industries.head
        data <- client.industries.getIndustryData(IndustryKey(firstIndustry.key))
        _ = assertEquals(data.name, firstIndustry.name)
        _ = assertEquals(data.sectorKey, "technology")
      } yield ()
    }
  }
}
