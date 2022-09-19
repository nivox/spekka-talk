name := "spekka-talk"

scalaVersion := "2.13.8"

scalacOptions := Seq(
    "-deprecation"
)

resolvers += "Sonatype OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"
resolvers += "Sonatype OSS Releases" at "https://s01.oss.sonatype.org/content/repositories/releases"

val AkkaVersion = "2.6.19"
val SpekkaVersion = "0.1.1-RC5"

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
    "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-tools" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,

    "com.typesafe.akka" %% "akka-persistence-cassandra" % "1.0.5",
    
    "com.typesafe.akka" %% "akka-stream-kafka" % "2.1.1",

    "com.typesafe.akka" %% "akka-http" % "10.2.9",

    "io.github.spekka" %% "spekka-context" % SpekkaVersion,
    "io.github.spekka" %% "spekka-stateful" % SpekkaVersion,
    "io.github.spekka" %% "spekka-stateful-akkapersistence" % SpekkaVersion,
    "io.github.spekka" %% "spekka-stateful-sharding" % SpekkaVersion,

    "io.circe" %% "circe-core" % "0.14.1",
    "io.circe" %% "circe-generic" % "0.14.1",
    "io.circe" %% "circe-parser" % "0.14.1",

    "ch.qos.logback" % "logback-classic" % "1.2.11"
)

enablePlugins(PackPlugin)