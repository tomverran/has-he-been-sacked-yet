name := "hypocrites-be-gone"

version := "0.1"

scalaVersion := "2.13.2"

val http4sVersion = "0.21.4"
val catsEffectVersion = "2.1.3"
val catsVersion = "2.1.1"

libraryDependencies ++= Seq(
  "org.typelevel"  %% "cats-core"           % catsVersion,
  "org.typelevel"  %% "cats-effect"         % catsEffectVersion,
  "org.http4s"     %% "http4s-blaze-server" % http4sVersion,
  "org.http4s"     %% "http4s-dsl"          % http4sVersion,
  "dev.profunktor" %% "redis4cats-effects"  % "0.9.6",
  "ch.qos.logback" % "logback-classic"      % "1.2.3"
)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
enablePlugins(JavaAppPackaging)