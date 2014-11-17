import sbt._
import Common._

sbtPlugin := true

name := "microservices-sandbox"

version := "1.0.1-SNAPSHOT"

crossScalaVersions := crossScalaBuildingSeq

organization := organisationString

organizationName := organisationNameString

scalacOptions := scalaOptionsSeq

publishTo.<<=(publishResolver)

credentials += sbtCredentials

libraryDependencies ++= Seq(
  "commons-io" % "commons-io" % "2.4" withSources() withJavadoc(),
  "com.typesafe" % "config" % "1.2.1" withSources() withJavadoc()
)

lazy val microservicesSanbox = project in file("microservices-sandbox")

lazy val root = project.in(file(".")).aggregate(microservicesSanbox)

