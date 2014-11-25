import Common._

sbtPlugin := true

name := "build-details-generator"

version := "1.0.0"

scalaVersion := scalaVersionString

organization := organisationString

organizationName := organisationNameString

scalacOptions := scalaOptionsSeq

publishTo.<<=(publishResolver)

credentials += sbtCredentials

