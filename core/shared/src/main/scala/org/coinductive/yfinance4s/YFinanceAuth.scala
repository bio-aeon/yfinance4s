package org.coinductive.yfinance4s

import cats.effect.{Async, Ref, Resource}
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import retry.{RetryPolicies, RetryPolicy, Sleep}
import sttp.client3.*
import sttp.model.Header

import scala.concurrent.duration.FiniteDuration

final case class YFinanceCredentials(
    cookies: List[String],
    crumb: String
)

sealed trait YFinanceAuth[F[_]] {
  def getCredentials: F[YFinanceCredentials]
  def refreshCredentials: F[YFinanceCredentials]
}

private object YFinanceAuth {

  private val CookiePrimeUrl = uri"https://fc.yahoo.com/"
  private val CrumbUrl = uri"https://query1.finance.yahoo.com/v1/test/getcrumb"

  private val BrowserHeaders: List[Header] = List(
    Header(
      "User-Agent",
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
    ),
    Header(
      "Accept",
      "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
    ),
    Header("Accept-Language", "en-US,en;q=0.9"),
    Header("Accept-Encoding", "gzip, deflate, br"),
    Header("Sec-Ch-Ua", "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"121\", \"Chromium\";v=\"121\""),
    Header("Sec-Ch-Ua-Mobile", "?0"),
    Header("Sec-Ch-Ua-Platform", "\"Windows\""),
    Header("Sec-Fetch-Dest", "document"),
    Header("Sec-Fetch-Mode", "navigate"),
    Header("Sec-Fetch-Site", "none"),
    Header("Sec-Fetch-User", "?1"),
    Header("Upgrade-Insecure-Requests", "1")
  )

  private val CrumbHeaders: List[Header] = List(
    Header(
      "User-Agent",
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
    ),
    Header("Accept", "*/*"),
    Header("Accept-Language", "en-US,en;q=0.9"),
    Header("Sec-Ch-Ua", "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"121\", \"Chromium\";v=\"121\""),
    Header("Sec-Ch-Ua-Mobile", "?0"),
    Header("Sec-Ch-Ua-Platform", "\"Windows\""),
    Header("Sec-Fetch-Dest", "empty"),
    Header("Sec-Fetch-Mode", "cors"),
    Header("Sec-Fetch-Site", "same-site"),
    Header("Origin", "https://finance.yahoo.com"),
    Header("Referer", "https://finance.yahoo.com/")
  )

  private val ApiHeaders: List[Header] = List(
    Header(
      "User-Agent",
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
    ),
    Header("Accept", "application/json"),
    Header("Accept-Language", "en-US,en;q=0.9"),
    Header("Sec-Ch-Ua", "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"121\", \"Chromium\";v=\"121\""),
    Header("Sec-Ch-Ua-Mobile", "?0"),
    Header("Sec-Ch-Ua-Platform", "\"Windows\""),
    Header("Sec-Fetch-Dest", "empty"),
    Header("Sec-Fetch-Mode", "cors"),
    Header("Sec-Fetch-Site", "same-site"),
    Header("Origin", "https://finance.yahoo.com"),
    Header("Referer", "https://finance.yahoo.com/")
  )

  def resource[F[_]: Async](
      connectTimeout: FiniteDuration,
      readTimeout: FiniteDuration,
      retries: Int
  ): Resource[F, YFinanceAuth[F]] =
    PlatformSttpBackend.resource[F](connectTimeout, readTimeout).evalMap { backend =>
      val retryPolicy = RetryPolicies.limitRetries[F](retries)
      Ref.of[F, Option[YFinanceCredentials]](None).map { credentialsRef =>
        new YFinanceAuthImpl[F](backend, retryPolicy, credentialsRef)
      }
    }

  private final class YFinanceAuthImpl[F[_]](
      sttpBackend: SttpBackend[F, Any],
      retryPolicy: RetryPolicy[F],
      credentialsRef: Ref[F, Option[YFinanceCredentials]]
  )(implicit F: Async[F], S: Sleep[F])
      extends YFinanceAuth[F] {

    override def getCredentials: F[YFinanceCredentials] =
      credentialsRef.get.flatMap {
        case Some(creds) => F.pure(creds)
        case None        => refreshCredentials
      }

    override def refreshCredentials: F[YFinanceCredentials] =
      for {
        cookies <- primeCookies
        crumb <- fetchCrumb(cookies)
        creds = YFinanceCredentials(cookies, crumb)
        _ <- credentialsRef.set(Some(creds))
      } yield creds

    private def primeCookies: F[List[String]] = {
      val request = basicRequest
        .get(CookiePrimeUrl)
        .headers(BrowserHeaders *)
        .followRedirects(true)
        .response(asString)

      retry
        .retryingOnAllErrors(policy = retryPolicy, (_: Throwable, _) => F.unit) {
          request.send(sttpBackend)
        }
        .map { response =>
          response.headers
            .filter(_.name.equalsIgnoreCase("Set-Cookie"))
            .map(_.value)
            .map(extractCookieNameValue)
            .toList
        }
    }

    private def fetchCrumb(cookies: List[String]): F[String] = {
      val cookieHeader = cookies.mkString("; ")
      val request = basicRequest
        .get(CrumbUrl)
        .headers(CrumbHeaders *)
        .header("Cookie", cookieHeader)
        .followRedirects(true)
        .response(asString)

      retry
        .retryingOnAllErrors(policy = retryPolicy, (_: Throwable, _) => F.unit) {
          request.send(sttpBackend)
        }
        .flatMap { response =>
          response.body match {
            case Right(crumb) if !crumb.contains("Too Many Requests") && crumb.nonEmpty =>
              F.pure(crumb.trim)
            case Right(errorBody) =>
              F.raiseError(new Exception(s"Failed to fetch crumb: $errorBody"))
            case Left(error) =>
              F.raiseError(new Exception(s"Failed to fetch crumb: $error"))
          }
        }
    }

    private def extractCookieNameValue(setCookie: String): String = {
      setCookie.split(";").headOption.getOrElse(setCookie)
    }
  }

  def apiHeaders: List[Header] = ApiHeaders
}
