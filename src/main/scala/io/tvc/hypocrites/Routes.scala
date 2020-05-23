package io.tvc.hypocrites

import cats.effect.Sync
import cats.instances.option._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import org.http4s.EntityEncoder.stringEncoder
import org.http4s.MediaType.text
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Cookie, `Content-Type`, `Set-Cookie`, `X-Forwarded-For`}
import org.http4s.{EntityEncoder, HttpRoutes, Request, ResponseCookie}

import scala.io.Source

class Routes[F[_]: Sync](store: VoteStore[F]) extends Http4sDsl[F] {

  val template: String = {
    val source = Source.fromURL(getClass.getResource("/index.html"))
    val content = source.getLines.mkString("")
    source.close()
    content
  }

  implicit val encode: EntityEncoder[F, String] =
    stringEncoder[F].withContentType(`Content-Type`(text.html))

  def homepage(req: Request[F]): F[String] =
    for {
      votes <- store.total
      voted = req.headers.get(Cookie).exists(_.name == "voted")
    } yield template
      .replace("{{count}}", votes.toString)
      .replace("{{status}}", if (voted) "has_voted" else "not_voted")

  val routes: HttpRoutes[F] =
    HttpRoutes.of {
      case req @ GET -> Root =>
        homepage(req).flatMap(Ok(_))
      case req @ POST -> Root =>
        (
          for {
            header <- req.headers.get(`X-Forwarded-For`)
            original <- header.values.head
          } yield original
        ).traverse(store.logVote)
         .flatMap(_.as(Created(homepage(req))).getOrElse(BadRequest(homepage(req))))
         .map(_.withHeaders(`Set-Cookie`(ResponseCookie("voted", "true"))))
    }
}
