package io.tvc.hypocrites

import cats.effect.Sync
import cats.instances.option._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import org.http4s.EntityEncoder.stringEncoder
import org.http4s.HttpDate.MaxValue
import org.http4s.MediaType.text
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{`Content-Type`, `Set-Cookie`, `X-Forwarded-For`}
import org.http4s.{EntityEncoder, HttpRoutes, Request, ResponseCookie}

import scala.io.Source

class Routes[F[_]: Sync](store: VoteStore[F]) extends Http4sDsl[F] {

  val template: String = {
    val source = Source.fromURL(getClass.getResource("/index.html"))
    val content = source.getLines.mkString("")
    source.close()
    content
  }

  val votedCookie: ResponseCookie =
    ResponseCookie(
      name = "voted",
      content = "1",
      expires = Some(MaxValue),
      httpOnly = true,
      path = Some("/")
    )

  implicit val encode: EntityEncoder[F, String] =
    stringEncoder[F].withContentType(`Content-Type`(text.html))

  def hasVotedCookie(req: Request[F]): Boolean =
    req.cookies.exists(_.name == "voted")

  def homepage(voted: Boolean): F[String] =
    store.total.map { votes =>
      template
        .replace("{{count}}", votes.toString)
        .replace("{{status}}", if (voted) "has_voted" else "not_voted")
    }

  val routes: HttpRoutes[F] =
    HttpRoutes.of {
      case req @ GET -> Root =>
        homepage(hasVotedCookie(req)).flatMap(Ok(_))
      case req @ POST -> Root =>
        (
          for {
            header <- req.headers.get(`X-Forwarded-For`)
            original <- header.values.head
          } yield original
        ).traverse(store.logVote)
         .flatMap(_.as(Created(homepage(true))).getOrElse(BadRequest(homepage(false))))
         .map(_.withHeaders(`Set-Cookie`(votedCookie)))
    }
}
