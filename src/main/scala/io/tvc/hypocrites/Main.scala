package io.tvc.hypocrites

import cats.effect.{ExitCode, IO, IOApp, Resource}
import dev.profunktor.redis4cats.effect.Log
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._

import scala.concurrent.ExecutionContext.global

object Main extends IOApp {

  implicit val log: Log[IO] =
    Log.NoOp.instance

  val config: IO[(String, Int)] =
    IO.fromOption(
      for {
        url <- sys.env.get("REDIS_URL")
        port <- sys.env.get("PORT").map(_.toInt) // sorry
      } yield (url, port + 1) // port + 1 means we get SSL
    )(new Exception("All you needed is two env vars and still you fail."))

  def httpApp(routes: Routes[IO], port: Int): Resource[IO, Server[IO]] =
    BlazeServerBuilder[IO](global)
      .withoutBanner
      .bindHttp(port, "0.0.0.0")
      .withHttpApp(routes.routes.orNotFound)
      .resource

  def run(args: List[String]): IO[ExitCode] =
    (
      for {
        (url, port) <- Resource.liftF(config)
        store       <- VoteStore[IO](url)
        _           <- httpApp(new Routes[IO](store), port)
      } yield ExitCode.Error
    ).use(IO.never.as(_)) // not my best work
}
