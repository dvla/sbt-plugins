import Common._
import bintray.Keys._

sbtPlugin := true

name := "build-details-generator"

version := "1.0.0"

scalaVersion := scalaVersionString

organization := organisationString

organizationName := organisationNameString

scalacOptions := scalaOptionsSeq

publishTo.<<=(publishResolver)

credentials += sbtCredentials

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

bintrayPublishSettings

bintrayOrganization := None

repository := "maven"

publishMavenStyle := true

BintrayCredentials.bintrayCredentialsTask

