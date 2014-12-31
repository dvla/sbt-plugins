package uk.gov.dvla.vehicles.sandbox

import SandboxSettings._
import Runner._
import Tasks._
import PrerequisitesCheck.prerequisitesCheck
import sbt._

object Sandbox extends AutoPlugin {
  override def trigger = allRequirements

  lazy val sandboxTask = sandbox :=
    runSequentially(prerequisitesCheck, setMicroservicesPortsEnvVars, runAppAndMicroservices).value

  lazy val sandboxAsyncTask = sandboxAsync :=
    runSequentially(prerequisitesCheck, setMicroservicesPortsEnvVars, runAppAndMicroservicesAsync).value

  lazy val gatlingTask = gatling := runSequentially(sandboxAsync, testGatling).value

  lazy val acceptTask = accept := runSequentially(sandboxAsync, allAcceptanceTests).value

  override def projectSettings = Seq(
    SandboxSettings.bruteForceEnabled := false
  )
}
