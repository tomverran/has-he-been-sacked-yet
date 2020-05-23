package io.tvc.hypocrites

import java.net.InetAddress
import java.time.Instant

import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.flatMap._
import dev.profunktor.redis4cats.codecs.Codecs
import dev.profunktor.redis4cats.codecs.splits.SplitEpi
import dev.profunktor.redis4cats.connection.{RedisClient, RedisURI}
import dev.profunktor.redis4cats.domain.RedisCodec
import dev.profunktor.redis4cats.domain.RedisCodec.Utf8
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.interpreter.Redis
import cats.syntax.functor._

/**
 * And to think people say e-voting is a bad idea,
 * look we can just use Redis and it'll be fine
 */
trait VoteStore[F[_]] {
  def logVote(ip: InetAddress): F[Unit]
  def total: F[Long]
}

object VoteStore {

  /**
   * Comically unsafe but I'm two glasses of wine in at this point
   * and frankly struggling to even pronounce SplitEpi
   */
  val codec: RedisCodec[String, Long] =
    Codecs.derive[String, Long](Utf8, SplitEpi(_.toLong, _.toString))

  /**
   * Heroku sets up an stunnel to redis sowe can use SSL but have to disable verification
   * Yes, this Heroku specific behaviour here is bad, what can I say. I'm tired.
   */
  def sslUri[F[_]: Sync](url: String): Resource[F, RedisURI] =
    Resource.liftF(
      RedisURI.make(url).flatTap { url =>
        Sync[F].delay {
          url.underlying.setVerifyPeer(false)
          url.underlying.setSsl(true)
        }
      }
    )

  /**
   * Create a VoteStore that hits Redis,
   * our beloved key/value storing friend
   */
  def apply[F[_]: Concurrent: ContextShift: Log](
    url: String
  )(implicit F: Sync[F]): Resource[F, VoteStore[F]] =
    (
      for {
        uri <- sslUri(url)
        client <- RedisClient(uri)
        redis <- Redis(client, codec)
      } yield redis
    ).map { redis =>
      new VoteStore[F] {
        def logVote(ip: InetAddress): F[Unit] =
          Sync[F].ifM(redis.exists(ip.getHostAddress))(
            ifTrue = F.unit,
            ifFalse = for {
              time <- F.delay(Instant.now)
              _ <- redis.set(ip.getHostAddress, time.toEpochMilli)
              _ <- redis.incr("count")
            } yield ()
          )
        val total: F[Long] =
          redis.get("count").map(_.getOrElse(0L))
      }
    }
}
