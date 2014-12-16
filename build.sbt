import Common._

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
  bintray.Keys.bintrayOrganization := None,
  bintray.Keys.repository := "maven"
) //++ bintrayPublishSettings // Comment this in before pushing to GitHub so it gets automatically built and published to bintray

lazy val microservicesSandbox = project.in(file("microservices-sandbox"))
  .settings(name := "microservices-sandbox")
  .settings(version := "1.1-SNAPSHOT")
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
  .settings(version := "1.1-SNAPSHOT")
  .settings(commonSettings:_*)
  .settings(publishMavenStyle := true)

publish := {}
