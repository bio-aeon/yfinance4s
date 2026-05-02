package org.coinductive.yfinance4s

import cats.Monad
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import org.coinductive.yfinance4s.Mapping.*
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.internal.*

import java.time.{LocalDate, ZoneOffset}

/** Algebra for options chain data. */
trait Options[F[_]] {

  /** Retrieves all available option expiration dates for a ticker. */
  def getOptionExpirations(ticker: Ticker): F[Option[List[LocalDate]]]

  /** Retrieves the option chain for a specific expiration date. */
  def getOptionChain(ticker: Ticker, expirationDate: LocalDate): F[Option[OptionChain]]

  /** Retrieves the nearest expiration's option chain along with all available expirations. */
  def getFullOptionChain(ticker: Ticker): F[Option[FullOptionChain]]
}

private[yfinance4s] object Options {

  def apply[F[_]: Monad](gateway: YFinanceGateway[F], auth: YFinanceAuth[F]): Options[F] =
    new OptionsImpl(gateway, auth)

  private final class OptionsImpl[F[_]: Monad](
      gateway: YFinanceGateway[F],
      auth: YFinanceAuth[F]
  ) extends Options[F] {

    def getOptionExpirations(ticker: Ticker): F[Option[List[LocalDate]]] =
      auth.getCredentials.flatMap(creds => gateway.getOptions(ticker, creds).map(extractExpirations))

    def getOptionChain(ticker: Ticker, expirationDate: LocalDate): F[Option[OptionChain]] = {
      val epochSeconds = expirationDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond
      auth.getCredentials.flatMap { creds =>
        gateway.getOptions(ticker, epochSeconds, creds).map(extractOptionChain(_, expirationDate))
      }
    }

    def getFullOptionChain(ticker: Ticker): F[Option[FullOptionChain]] =
      auth.getCredentials.flatMap(creds => gateway.getOptions(ticker, creds).map(mapToFullOptionChain))

    // --- Private Mapping Helpers ---

    private def extractExpirations(response: OptionChainResponse): Option[List[LocalDate]] =
      response.result.headOption.map { data =>
        data.expirationDates.map(epochToLocalDate).sorted
      }

    private def extractOptionChain(response: OptionChainResponse, requestedDate: LocalDate): Option[OptionChain] =
      response.result.headOption.flatMap { data =>
        data.options
          .find(container => epochToLocalDate(container.expirationDate) == requestedDate)
          .map(container => buildOptionChain(container, data.strikes))
      }

    private def mapToFullOptionChain(response: OptionChainResponse): Option[FullOptionChain] =
      response.result.headOption.map { data =>
        val expirations = data.expirationDates.map(epochToLocalDate).sorted
        val underlyingPrice = data.quote.flatMap(_.regularMarketPrice)

        val chains = data.options.map { container =>
          val date = epochToLocalDate(container.expirationDate)
          date -> buildOptionChain(container, data.strikes)
        }.toMap

        FullOptionChain(
          underlyingSymbol = data.underlyingSymbol,
          underlyingPrice = underlyingPrice,
          expirationDates = expirations,
          chains = chains
        )
      }

    private def buildOptionChain(container: OptionsContainerRaw, allStrikes: List[Double]): OptionChain = {
      val expirationDate = epochToLocalDate(container.expirationDate)
      val calls = container.calls.map(rawToContract(_, OptionType.Call, expirationDate)).sorted
      val puts = container.puts.map(rawToContract(_, OptionType.Put, expirationDate)).sorted
      val activeStrikes = (calls.map(_.strike) ++ puts.map(_.strike)).distinct.sorted

      OptionChain(
        expirationDate = expirationDate,
        calls = calls,
        puts = puts,
        strikes = activeStrikes,
        hasMiniOptions = container.hasMiniOptions
      )
    }

    private def rawToContract(
        raw: OptionContractRaw,
        optionType: OptionType,
        expiration: LocalDate
    ): OptionContract =
      OptionContract(
        contractSymbol = raw.contractSymbol,
        optionType = optionType,
        strike = raw.strike,
        expiration = expiration,
        currency = raw.currency,
        lastPrice = raw.lastPrice,
        change = raw.change,
        percentChange = raw.percentChange,
        bid = raw.bid,
        ask = raw.ask,
        volume = raw.volume,
        openInterest = raw.openInterest,
        impliedVolatility = raw.impliedVolatility.map(_ * 100.0),
        inTheMoney = raw.inTheMoney,
        lastTradeDate = raw.lastTradeDate.map(epochToZonedDateTime),
        contractSize = raw.contractSize.map(ContractSize.fromString).getOrElse(ContractSize.Regular)
      )
  }
}
