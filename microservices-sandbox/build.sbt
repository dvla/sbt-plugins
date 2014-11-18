import sbt._
import Common._

sbtPlugin := true

name := "microservices-sandbox"

version := "1.0.1-SNAPSHOT"

scalaVersion := scalaVersionString

organization := organisationString

organizationName := organisationNameString

scalacOptions := scalaOptionsSeq

publishTo.<<=(publishResolver)

credentials += sbtCredentials

libraryDependencies ++= Seq(
  "commons-io" % "commons-io" % "2.4" withSources() withJavadoc(),
  "com.typesafe" % "config" % "1.2.1" withSources() withJavadoc()
)

