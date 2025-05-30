package org.coinductive.yfinance4s

import cats.effect.{Async, Resource, Sync}
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import io.circe.parser.decode
import org.coinductive.yfinance4s.models.{Ticker, YFinanceQuoteResult}
import org.jsoup.Jsoup
import retry.{RetryPolicies, RetryPolicy, Sleep}
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.{SttpBackend, UriContext, basicRequest}

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

sealed trait YFinanceScrapper[F[_]] {
  def getQuote(ticker: Ticker): F[Option[YFinanceQuoteResult]]
}

private object YFinanceScrapper {

  def resource[F[_]: Async](
      connectTimeout: FiniteDuration,
      readTimeout: FiniteDuration,
      retries: Int
  ): Resource[F, YFinanceScrapper[F]] = {
    val connectTimeoutMs = connectTimeout.toMillis.toInt
    val readTimeoutMs = readTimeout.toMillis.toInt

    AsyncHttpClientCatsBackend
      .resourceUsingConfigBuilder[F](
        _.setConnectTimeout(connectTimeoutMs)
          .setReadTimeout(readTimeoutMs)
          .setRequestTimeout(readTimeoutMs)
          .setHttpClientCodecMaxHeaderSize(32768)
      )
      .map(apply[F](retries, _))
  }

  def apply[F[_]: Sync: Sleep](retries: Int, sttpBackend: SttpBackend[F, Any]): YFinanceScrapper[F] = {
    val retryPolicy = RetryPolicies.limitRetries(retries)
    new YFinanceScrapperImpl[F](sttpBackend, retryPolicy)
  }

  private final class YFinanceScrapperImpl[F[_]](
      protected val sttpBackend: SttpBackend[F, Any],
      protected val retryPolicy: RetryPolicy[F]
  )(implicit
      protected val F: Sync[F],
      protected val S: Sleep[F]
  ) extends HTTPBase[F]
      with YFinanceScrapper[F] {

    private val QuoteEndpoint = uri"https://finance.yahoo.com/quote/"
    private val QuoteSummaryUrl = "https://query1.finance.yahoo.com/v10/finance/quoteSummary/"
    private val FundamentalsTimeSeriesUrl =
      "https://query1.finance.yahoo.com/ws/fundamentals-timeseries/v1/finance/timeseries/"

    def getQuote(ticker: Ticker): F[Option[YFinanceQuoteResult]] = {
      val req = basicRequest.get(QuoteEndpoint.addPath(ticker.show))
      sendRequest(req, parseContent)
    }

    private def parseContent(content: String): F[Option[YFinanceQuoteResult]] = {
      val doc = Jsoup.parse(content)
      val elements = doc.select("script[data-sveltekit-fetched][data-url]")
      val (maybeSummary, maybeFundamentals) =
        elements.asScala.foldLeft[(Option[String], Option[String])]((None, None)) {
          case ((summaryStr, fundamentalsStr), element) =>
            val dataUrl = element.attr("data-url")
            val jsonData = element.html
            if (dataUrl.contains(QuoteSummaryUrl)) {
              (Either.cond(jsonData.nonEmpty, Some(jsonData), None).merge, fundamentalsStr)
            } else if (dataUrl.contains(FundamentalsTimeSeriesUrl)) {
              (summaryStr, Either.cond(jsonData.nonEmpty, Some(jsonData), None).merge)
            } else {
              (summaryStr, fundamentalsStr)
            }
        }

      (maybeSummary, maybeFundamentals).traverseN { case (summaryStr, fundamentalsStr) =>
        for {
          summary <- decode[YFinanceQuoteResult.Summary](summaryStr)
            .fold(
              e => F.raiseError(new Exception(s"Illegible quote summary: ${e.getMessage}")),
              F.pure
            )
          fundamentals <- decode[YFinanceQuoteResult.Fundamentals](fundamentalsStr)
            .fold(
              e => F.raiseError(new Exception(s"Illegible quote fundamentals: ${e.getMessage}")),
              F.pure
            )
        } yield YFinanceQuoteResult(summary, fundamentals)
      }
    }

  }

}
