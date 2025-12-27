package org.coinductive.yfinance4s.models

import cats.syntax.either.*
import cats.syntax.traverse.*
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode
import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import org.coinductive.yfinance4s.models.YFinanceQuoteResult.{Fundamentals, Summary}

private[yfinance4s] final case class YFinanceQuoteResult(summary: Summary, fundamentals: Fundamentals)

private[yfinance4s] object YFinanceQuoteResult {

  private[yfinance4s] final case class Summary(body: SummaryBody)

  object Summary {
    implicit val decoder: Decoder[Summary] =
      (c: HCursor) => {
        for {
          bodyStr <- c.downField("body").as[String]
          body <- decode[SummaryBody](bodyStr).leftMap(DecodingFailure.fromThrowable(_, Nil))
        } yield Summary(body)
      }
  }

  private[yfinance4s] final case class Fundamentals(body: FundamentalsBody)

  object Fundamentals {
    implicit val decoder: Decoder[Fundamentals] =
      (c: HCursor) => {
        for {
          bodyStr <- c.downField("body").as[String]
          body <- decode[FundamentalsBody](bodyStr).leftMap(DecodingFailure.fromThrowable(_, Nil))
        } yield Fundamentals(body)
      }
  }

  private[yfinance4s] final case class SummaryBody(quoteSummary: QuoteSummary)

  private[yfinance4s] object SummaryBody {
    implicit val decoder: Decoder[SummaryBody] = deriveDecoder
  }

  private[yfinance4s] final case class FundamentalsBody(timeseries: TimeSeries)

  private[yfinance4s] object FundamentalsBody {
    implicit val decoder: Decoder[FundamentalsBody] = deriveDecoder
  }

  private[yfinance4s] final case class QuoteSummary(result: List[QuoteData])

  private[yfinance4s] final case class TimeSeries(result: Option[TimeSeriesData])

  private[yfinance4s] object TimeSeries {
    implicit val decoder: Decoder[TimeSeries] =
      (c: HCursor) => {
        for {
          result <- c.downField("result").as[List[Json]]
          dataJson = result.find(_.asObject.exists(_.contains("trailingPegRatio")))
          data <- dataJson.traverse(_.as[TimeSeriesData].leftMap(DecodingFailure.fromThrowable(_, Nil)))
        } yield TimeSeries(data)
      }
  }

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

  private[yfinance4s] final case class TimeSeriesData(trailingPegRatio: List[TrailingPegRatio])

  private[yfinance4s] object TimeSeriesData {
    implicit val decoder: Decoder[TimeSeriesData] = deriveDecoder
  }

  private[yfinance4s] final case class TrailingPegRatio(reportedValue: Value[Double])

  private[yfinance4s] object TrailingPegRatio {
    implicit val decoder: Decoder[TrailingPegRatio] = deriveDecoder
  }

  private[yfinance4s] final case class Value[A](raw: A)

  private[yfinance4s] object Value {
    implicit def decoder[A: Decoder]: Decoder[Value[A]] = deriveDecoder
  }
}
