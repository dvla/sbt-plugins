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
  BintrayCredentials.bintrayCredentialsTask,
  bintray.Keys.bintrayOrganization := None
) //++ bintrayPublishSettings // Comment this in before pushing to GitHub so it gets automatically built and published to bintray

lazy val microservicesSandbox = Project("microservices-sandbox", file("microservices-sandbox"))
  .settings(version := "1.3.5")
  .settings(commonSettings:_*)
  .settings(
    libraryDependencies ++= Seq(
      "commons-io" % "commons-io" % "2.4" withSources() withJavadoc(),
      "com.typesafe" % "config" % "1.2.1" withSources() withJavadoc()
    )
  )

lazy val buildDetailsGenerator = Project("build-details-generator", file("build-details-generator"))
  .settings(version := "1.3.2-SNAPSHOT")
  .settings(commonSettings:_*)

publish := {}
