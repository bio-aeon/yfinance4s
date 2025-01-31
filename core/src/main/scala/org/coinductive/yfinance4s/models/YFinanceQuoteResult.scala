package org.coinductive.yfinance4s.models

import cats.syntax.either._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode
import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import org.coinductive.yfinance4s.models.YFinanceQuoteResult.Body

private[yfinance4s] final case class YFinanceQuoteResult(body: Body)

private[yfinance4s] object YFinanceQuoteResult {

  private[yfinance4s] final case class Body(quoteSummary: QuoteSummary)

  private[yfinance4s] object Body {
    implicit val decoder: Decoder[Body] = deriveDecoder
  }

  private[yfinance4s] final case class QuoteSummary(result: List[QuoteData])

  private[yfinance4s] object QuoteSummary {
    implicit val decoder: Decoder[QuoteSummary] = deriveDecoder
  }

  private[yfinance4s] final case class QuoteData(
      defaultKeyStatistics: DefaultKeyStatistics,
      financialData: FinancialData,
      summaryDetail: SummaryDetail,
      summaryProfile: SummaryProfile,
      price: Price
  )

  private[yfinance4s] object QuoteData {
    implicit val decoder: Decoder[QuoteData] = deriveDecoder
  }

  private[yfinance4s] final case class DefaultKeyStatistics(
      enterpriseValue: Value[Long],
      floatShares: Value[Long],
      sharesOutstanding: Value[Long],
      sharesShort: Value[Long],
      shortRatio: Value[Double],
      shortPercentOfFloat: Value[Double],
      impliedSharesOutstanding: Value[Long],
      netIncomeToCommon: Value[Double],
      pegRatio: Value[Option[Float]],
      enterpriseToRevenue: Value[Float],
      enterpriseToEbitda: Value[Float],
      bookValue: Option[Value[Double]],
      priceToBook: Option[Value[Double]],
      trailingEps: Option[Value[Double]],
      forwardEps: Option[Value[Double]]
  )

  private[yfinance4s] object DefaultKeyStatistics {
    implicit val decoder: Decoder[DefaultKeyStatistics] = deriveDecoder
  }

  private[yfinance4s] final case class FinancialData(
      totalCash: Value[Long],
      totalDebt: Value[Long],
      totalRevenue: Value[Long],
      ebitda: Value[Long],
      debtToEquity: Value[Float],
      revenuePerShare: Value[Float],
      returnOnAssets: Value[Float],
      returnOnEquity: Value[Float],
      freeCashflow: Value[Long],
      operatingCashflow: Value[Long],
      earningsGrowth: Value[Float],
      revenueGrowth: Value[Float],
      grossMargins: Value[Float],
      ebitdaMargins: Value[Float],
      operatingMargins: Value[Float],
      profitMargins: Value[Float]
  )

  private[yfinance4s] object FinancialData {
    implicit val decoder: Decoder[FinancialData] = deriveDecoder
  }

  private[yfinance4s] final case class SummaryDetail(
      trailingPE: Option[Value[Double]],
      forwardPE: Option[Value[Double]],
      dividendYield: Option[Value[Double]]
  )

  private[yfinance4s] object SummaryDetail {
    implicit val decoder: Decoder[SummaryDetail] = deriveDecoder
  }

  private[yfinance4s] final case class SummaryProfile(
      sector: Option[String],
      industry: Option[String],
      longBusinessSummary: Option[String]
  )

  private[yfinance4s] object SummaryProfile {
    implicit val decoder: Decoder[SummaryProfile] = deriveDecoder
  }

  private[yfinance4s] final case class Price(
      symbol: String,
      longName: String,
      quoteType: String,
      currency: String,
      regularMarketPrice: Value[Double],
      regularMarketChangePercent: Value[Double],
      marketCap: Value[Long],
      exchangeName: String
  )

  private[yfinance4s] object Price {
    implicit val decoder: Decoder[Price] = deriveDecoder
  }

  private[yfinance4s] final case class Value[A](raw: A)

  private[yfinance4s] object Value {
    implicit def decoder[A: Decoder]: Decoder[Value[A]] = deriveDecoder
  }

  implicit val decoder: Decoder[YFinanceQuoteResult] =
    (c: HCursor) => {
      for {
        bodyStr <- c.downField("body").as[String]
        body <- decode[Body](bodyStr).leftMap(DecodingFailure.fromThrowable(_, Nil))
      } yield YFinanceQuoteResult(body)
    }
}
