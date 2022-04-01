name := "spekka-talk"

scalaVersion := "2.13.8"

val AkkaVersion = "2.6.19"

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream-kafka" % "2.1.1",

    "com.typesafe.akka" %% "akka-http" % "10.2.9",

    "io.github.spekka" %% "spekka-context" % "0.1.0-rc2",
    "io.github.spekka" %% "spekka-stateful" % "0.1.0-rc2",

    "io.circe" %% "circe-core" % "0.14.1",
    "io.circe" %% "circe-generic" % "0.14.1",
    "io.circe" %% "circe-parser" % "0.14.1",

    "ch.qos.logback" % "logback-classic" % "1.2.11"
)

enablePlugins(PackPlugin)