package org.coinductive.yfinance4s

import cats.effect.{Async, Resource, Sync}
import cats.syntax.show._
import cats.syntax.traverse._
import io.circe.parser.decode
import org.coinductive.yfinance4s.models.{Ticker, YFinanceQuoteResult}
import org.jsoup.Jsoup
import retry.{RetryPolicies, RetryPolicy, Sleep}
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.{SttpBackend, UriContext, basicRequest}
import sttp.model.{Header, HeaderNames}

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

    private val UserAgentHeader = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:134.0) Gecko/20100101 Firefox/134.0"

    def getQuote(ticker: Ticker): F[Option[YFinanceQuoteResult]] = {
      val req =
        basicRequest
          .headers(
            Header(
              HeaderNames.UserAgent,
              UserAgentHeader
            )
          )
          .get(QuoteEndpoint.addPath(ticker.show))

      sendRequest(req, parseContent)
    }

    private def parseContent(content: String): F[Option[YFinanceQuoteResult]] = {
      val doc = Jsoup.parse(content)
      val elements = doc.select("script[data-sveltekit-fetched][data-url]")
      elements.asScala
        .flatMap { element =>
          val dataUrl = element.attr("data-url")
          if (dataUrl.contains(QuoteSummaryUrl)) {
            val jsonData = element.html
            Either.cond(jsonData.nonEmpty, Some(jsonData), None).merge
          } else {
            None
          }
        }
        .headOption
        .traverse { data =>
          decode[YFinanceQuoteResult](data)
            .fold(
              e => F.raiseError(new Exception(s"Illegible quote data: ${e.getMessage}")),
              F.pure
            )
        }
    }

  }

}
