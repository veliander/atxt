name := """atxt"""
organization := "org.hexia"
version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.4" //allows use of flatten instead of flatMap(identity) for flattening nested Futures

libraryDependencies ++= Seq(
  guice,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
  "com.github.etaty" %% "rediscala" % "1.8.0",
  "com.oblac" % "nomen-est-omen" % "1.2.3",
  "com.typesafe.akka" %% "akka-actor" % "2.5.11",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.11" % Test,
  "com.typesafe"   % "config" % "1.3.2",
  "com.twilio.sdk" % "twilio" % "7.+"
)

