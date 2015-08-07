package uk.gov.dvla.vehicles.sandbox

import PrerequisitesCheck.prerequisitesCheck
import Runner.runSequentially
import SandboxSettings.accept
import SandboxSettings.acceptRemote
import SandboxSettings.acceptanceTests
import SandboxSettings.cucumber
import SandboxSettings.gatling
import SandboxSettings.loadTests
import SandboxSettings.sandbox
import SandboxSettings.sandboxAsync
import sbt.AutoPlugin
import uk.gov.dvla.vehicles.sandbox.Tasks.allAcceptanceTests
import uk.gov.dvla.vehicles.sandbox.Tasks.runAppAndMicroservices
import uk.gov.dvla.vehicles.sandbox.Tasks.runAppAndMicroservicesAsync
import uk.gov.dvla.vehicles.sandbox.Tasks.setMicroservicesPortsEnvVars

object Sandbox extends AutoPlugin {
  override def trigger = allRequirements

  lazy val sandboxTask = sandbox :=
    runSequentially(prerequisitesCheck, setMicroservicesPortsEnvVars, runAppAndMicroservices).value

  lazy val sandboxAsyncTask = sandboxAsync :=
    runSequentially(prerequisitesCheck, setMicroservicesPortsEnvVars, runAppAndMicroservicesAsync).value

  lazy val gatlingTask = gatling := runSequentially(sandboxAsync, loadTests).value

  lazy val cucumberTask = cucumber := runSequentially(sandboxAsync, acceptanceTests).value

  lazy val acceptTask = accept := runSequentially(sandboxAsync, allAcceptanceTests).value

  lazy val acceptRemoteTask = acceptRemote := allAcceptanceTests.value

  override def projectSettings = Seq(
    SandboxSettings.bruteForceEnabled := false,
    SandboxSettings.runAllMicroservices := {},
    SandboxSettings.loadTests := {}
  )
}
