package org.coinductive.yfinance4s

import cats.Functor
import cats.syntax.functor.*
import io.scalaland.chimney.dsl.*
import org.coinductive.yfinance4s.models.*
import org.coinductive.yfinance4s.models.internal.YFinanceFinancialsResult

/** Algebra for financial statements data. */
trait Financials[F[_]] {

  /** Retrieves comprehensive financial statements for a ticker. */
  def getFinancialStatements(
      ticker: Ticker,
      frequency: Frequency = Frequency.Yearly
  ): F[Option[FinancialStatements]]

  /** Retrieves income statements for a ticker. */
  def getIncomeStatements(
      ticker: Ticker,
      frequency: Frequency = Frequency.Yearly
  ): F[List[IncomeStatement]]

  /** Retrieves balance sheets for a ticker. */
  def getBalanceSheets(
      ticker: Ticker,
      frequency: Frequency = Frequency.Yearly
  ): F[List[BalanceSheet]]

  /** Retrieves cash flow statements for a ticker. */
  def getCashFlowStatements(
      ticker: Ticker,
      frequency: Frequency = Frequency.Yearly
  ): F[List[CashFlowStatement]]
}

private[yfinance4s] object Financials {

  private val DefaultCurrency = "USD"

  final class Impl[F[_]: Functor](gateway: YFinanceGateway[F]) extends Financials[F] {

    def getFinancialStatements(
        ticker: Ticker,
        frequency: Frequency
    ): F[Option[FinancialStatements]] =
      gateway.getFinancials(ticker, frequency).map(mapFinancialStatements(ticker, _))

    def getIncomeStatements(
        ticker: Ticker,
        frequency: Frequency
    ): F[List[IncomeStatement]] =
      gateway.getFinancials(ticker, frequency, "income").map(extractIncomeStatements)

    def getBalanceSheets(
        ticker: Ticker,
        frequency: Frequency
    ): F[List[BalanceSheet]] =
      gateway.getFinancials(ticker, frequency, "balance-sheet").map(extractBalanceSheets)

    def getCashFlowStatements(
        ticker: Ticker,
        frequency: Frequency
    ): F[List[CashFlowStatement]] =
      gateway.getFinancials(ticker, frequency, "cash-flow").map(extractCashFlowStatements)

    // --- Private Mapping Helpers ---

    private def mapFinancialStatements(
        ticker: Ticker,
        result: YFinanceFinancialsResult
    ): Option[FinancialStatements] = {
      val byDate = result.byDate
      if (byDate.isEmpty) return None

      val currency = byDate.values.headOption.map(_.currencyCode).getOrElse(DefaultCurrency)

      Some(
        FinancialStatements(
          ticker = ticker,
          currency = currency,
          incomeStatements = extractIncomeStatements(result),
          balanceSheets = extractBalanceSheets(result),
          cashFlowStatements = extractCashFlowStatements(result)
        )
      )
    }

    private def extractIncomeStatements(result: YFinanceFinancialsResult): List[IncomeStatement] =
      result.byDate.toList.map { case (date, raw) =>
        raw.income
          .into[IncomeStatement]
          .withFieldConst(_.reportDate, date)
          .withFieldConst(_.periodType, raw.periodType)
          .withFieldConst(_.currencyCode, raw.currencyCode)
          .withFieldRenamed(_.depreciationAndAmortizationInIncomeStatement, _.depreciationAndAmortization)
          .withFieldRenamed(_.basicEPS, _.basicEps)
          .withFieldRenamed(_.dilutedEPS, _.dilutedEps)
          .withFieldRenamed(_.eBIT, _.ebit)
          .withFieldRenamed(_.eBITDA, _.ebitda)
          .transform
      }.sorted

    private def extractBalanceSheets(result: YFinanceFinancialsResult): List[BalanceSheet] =
      result.byDate.toList.map { case (date, raw) =>
        raw.balance
          .into[BalanceSheet]
          .withFieldConst(_.reportDate, date)
          .withFieldConst(_.periodType, raw.periodType)
          .withFieldConst(_.currencyCode, raw.currencyCode)
          .withFieldRenamed(_.otherShortTermInvestments, _.shortTermInvestments)
          .withFieldRenamed(_.netPPE, _.netPpe)
          .withFieldRenamed(_.grossPPE, _.grossPpe)
          .withFieldRenamed(_.longTermEquityInvestment, _.longTermInvestments)
          .withFieldRenamed(_.totalNonCurrentLiabilitiesNetMinorityInterest, _.totalNonCurrentLiabilities)
          .withFieldRenamed(_.totalLiabilitiesNetMinorityInterest, _.totalLiabilities)
          .withFieldRenamed(_.shareIssued, _.sharesIssued)
          .transform
      }.sorted

    private def extractCashFlowStatements(result: YFinanceFinancialsResult): List[CashFlowStatement] =
      result.byDate.toList.map { case (date, raw) =>
        raw.cashFlow
          .into[CashFlowStatement]
          .withFieldConst(_.reportDate, date)
          .withFieldConst(_.periodType, raw.periodType)
          .withFieldConst(_.currencyCode, raw.currencyCode)
          .withFieldRenamed(_.changeInPayable, _.changeInPayables)
          .withFieldRenamed(_.netPPEPurchaseAndSale, _.netPpePurchaseAndSale)
          .transform
      }.sorted
  }
}
