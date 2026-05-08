package org.coinductive.yfinance4s.unit

import cats.effect.{IO, Sync}
import io.circe.Decoder
import munit.CatsEffectSuite
import org.coinductive.yfinance4s.{HTTPBase, RateLimiter}
import org.coinductive.yfinance4s.models.YFinanceError
import retry.{RetryPolicies, RetryPolicy, Sleep}
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Identity, RequestT, Response, SttpBackend, UriContext, basicRequest}
import sttp.model.{Header, StatusCode}

import scala.concurrent.duration.*

object HTTPBaseErrorMappingSpec {
  final case class Probe(value: Int)
  object Probe {
    implicit val decoder: Decoder[Probe] = Decoder.forProduct1("value")(Probe.apply)
  }
}

class HTTPBaseErrorMappingSpec extends CatsEffectSuite {

  import HTTPBaseErrorMappingSpec.Probe

  private final class TestHTTPBase(
      protected val sttpBackend: SttpBackend[IO, Any],
      protected val retryPolicy: RetryPolicy[IO] = RetryPolicies.limitRetries[IO](0),
      protected val rateLimiter: RateLimiter[IO] = RateLimiter.noop[IO]
  )(implicit
      protected val F: Sync[IO],
      protected val S: Sleep[IO]
  ) extends HTTPBase[IO] {

    def trySend[A](
        req: RequestT[Identity, Either[String, String], Any],
        parse: String => IO[A]
    ): IO[A] =
      sendRequest(req, parse)

    def asProbe(content: String): IO[Probe] = parseAs[Probe]("probe")(content)
  }

  private val probeRequest: RequestT[Identity, Either[String, String], Any] =
    basicRequest.get(uri"http://example.test/probe")

  private val passthroughParser: String => IO[String] = IO.pure(_)

  private val sttpMonad: sttp.monad.MonadError[IO] = new CatsMonadAsyncError[IO]

  private def stub(response: Response[Either[String, String]]): SttpBackend[IO, Any] =
    SttpBackendStub[IO, Any](sttpMonad).whenAnyRequest.thenRespond(response)

  private def stubFailing(throwable: Throwable): SttpBackend[IO, Any] =
    SttpBackendStub[IO, Any](sttpMonad).whenAnyRequest.thenRespondF(IO.raiseError(throwable))

  private def respondWith(
      body: Either[String, String],
      status: StatusCode,
      headers: Seq[Header] = Nil
  ): Response[Either[String, String]] =
    Response(body = body, code = status, statusText = status.toString, headers = headers, history = Nil, request = null)

  test("HTTP 429 with a numeric Retry-After header raises RateLimited carrying that duration") {
    val backend = stub(respondWith(Left("rate limited"), StatusCode.TooManyRequests, Seq(Header("Retry-After", "7"))))

    new TestHTTPBase(backend).trySend(probeRequest, passthroughParser).attempt.map {
      case Left(YFinanceError.RateLimited(Some(d))) => assertEquals(d, 7.seconds)
      case other                                    => fail(s"expected RateLimited(Some(7s)), got $other")
    }
  }

  test("HTTP 429 without a Retry-After header raises RateLimited with no duration") {
    val backend = stub(respondWith(Left("rate limited"), StatusCode.TooManyRequests))

    new TestHTTPBase(backend).trySend(probeRequest, passthroughParser).attempt.map {
      case Left(YFinanceError.RateLimited(None)) => ()
      case other                                 => fail(s"expected RateLimited(None), got $other")
    }
  }

  test("HTTP 429 with an unparseable Retry-After value raises RateLimited with no duration") {
    val backend =
      stub(respondWith(Left("rate limited"), StatusCode.TooManyRequests, Seq(Header("Retry-After", "soon"))))

    new TestHTTPBase(backend).trySend(probeRequest, passthroughParser).attempt.map {
      case Left(YFinanceError.RateLimited(None)) => ()
      case other                                 => fail(s"expected RateLimited(None), got $other")
    }
  }

  test("HTTP 5xx server error raises NetworkError") {
    val backend = stub(respondWith(Left("server is down"), StatusCode.InternalServerError))

    new TestHTTPBase(backend).trySend(probeRequest, passthroughParser).attempt.map {
      case Left(YFinanceError.NetworkError(msg, _)) =>
        assert(msg.contains("500"), s"expected status code in message: $msg")
      case other =>
        fail(s"expected NetworkError, got $other")
    }
  }

  test("HTTP 4xx other than 429 with a non-JSON body raises DataParseError carrying the parse cause") {
    val backend = stub(respondWith(Left("bad request"), StatusCode.BadRequest))
    val httpBase = new TestHTTPBase(backend)

    httpBase.trySend(probeRequest, httpBase.asProbe).attempt.map {
      case Left(YFinanceError.DataParseError(_, Some(_))) => ()
      case other => fail(s"expected DataParseError with parse cause, got $other")
    }
  }

  test("HTTP 4xx with a parseable JSON body decodes successfully so the algebra layer can inspect the error envelope") {
    val backend = stub(respondWith(Left("""{"value":42}"""), StatusCode.NotFound))
    val httpBase = new TestHTTPBase(backend)

    httpBase.trySend(probeRequest, httpBase.asProbe).map(p => assertEquals(p, Probe(42)))
  }

  test("transport-layer throw is wrapped as NetworkError carrying a non-empty cause") {
    val backend = stubFailing(new RuntimeException("connection refused"))

    new TestHTTPBase(backend).trySend(probeRequest, passthroughParser).attempt.map {
      case Left(YFinanceError.NetworkError(_, Some(_))) => ()
      case other                                        => fail(s"expected NetworkError carrying a cause, got $other")
    }
  }

  test("JSON decode failure on a successful response raises DataParseError carrying the parse cause") {
    val backend = stub(respondWith(Right("not json"), StatusCode.Ok))
    val httpBase = new TestHTTPBase(backend)

    httpBase.trySend(probeRequest, httpBase.asProbe).attempt.map {
      case Left(YFinanceError.DataParseError(msg, Some(_))) =>
        assert(msg.contains("probe"), s"expected label in message: $msg")
      case other =>
        fail(s"expected DataParseError with cause, got $other")
    }
  }
}
