import Common._
import bintray.Keys._

val commonSettings = Seq(
  sbtPlugin := true,
  scalaVersion := scalaVersionString,
  organization := organisationString,
  organizationName := organisationNameString,
  scalacOptions := scalaOptionsSeq,
  publishTo.<<=(publishResolver),
  credentials += sbtCredentials,
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  BintrayCredentials.bintrayCredentialsTask
) ++ Seq (
  bintrayOrganization := None,
  repository := "maven"
) ++ bintrayPublishSettings

lazy val microservicesSandbox = project.in(file("microservices-sandbox"))
  .settings(name := "microservices-sandbox")
  .settings(version := "1.0-SNAPSHOT")
  .settings(commonSettings:_*)
  .settings(
    libraryDependencies ++= Seq(
      "commons-io" % "commons-io" % "2.4" withSources() withJavadoc(),
      "com.typesafe" % "config" % "1.2.1" withSources() withJavadoc()
    )
  )
  .settings(publishMavenStyle := true)

lazy val buildDetailsGenerator = project.in(file("build-details-generator"))
  .settings(name := "build-details-generator")
  .settings(version := "1.0-SNAPSHOT")
  .settings(commonSettings:_*)
  .settings(publishMavenStyle := true)

publish := {}
