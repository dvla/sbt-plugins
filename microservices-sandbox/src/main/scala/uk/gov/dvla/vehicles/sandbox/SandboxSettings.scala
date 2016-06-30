package uk.gov.dvla.vehicles.sandbox

import sbt.{Project, settingKey, taskKey}

object SandboxSettings {
  lazy val applicationContext = settingKey[String]("The application context of the web app")
  lazy val portOffset = settingKey[Int]("The port offset for all the microservices.")
  lazy val acceptanceTests = taskKey[Unit]("A task for running the acceptance tests of the project.")
  lazy val loadTests = taskKey[Unit]("A task for running the load tests of the project.")
  lazy val runAllMicroservices = taskKey[Unit]("A task for running all the microservices needed by the sandbox.")
  lazy val webAppSecrets = settingKey[String]("The path to the application secret within the secrets repository.")

  lazy val sandbox = taskKey[Unit]("Runs the whole sandbox for manual testing including microservices, webapp and legacy stubs'")
  lazy val sandboxAsync = taskKey[Unit]("Runs the whole sandbox asynchronously for manual testing including microservices, webapp and legacy stubs")
  lazy val gatling = taskKey[Unit]("Runs the gatling only tests against the sandbox")
  lazy val cucumber = taskKey[Unit]("Runs the cucumber only tests against the sandbox")
  lazy val accept = taskKey[Unit]("Runs all the acceptance tests against the sandbox.")
  lazy val acceptRemote = taskKey[Unit]("Runs all the acceptance tests against a running application specified with the test.url system property.")

  lazy val legacyStubsProject = settingKey[Project]("The project definition for the Legacy Stubs Services")
  lazy val osAddressLookupProject = settingKey[Project]("The project definition for the os address lookup")
  lazy val vehicleAndKeeperLookupProject = settingKey[Project]("The project definition for the vehicle and keeper lookup project")
  lazy val vehiclesDisposeFulfilProject = settingKey[Project]("The project definition for vehicles dispose fulfil")
  lazy val vehiclesAcquireFulfilProject = settingKey[Project]("The project definition for vehicles acquire fulfil")
  lazy val paymentSolveProject = settingKey[Project]("The project definition for the payment solve project")
  lazy val vrmRetentionEligibilityProject = settingKey[Project]("The project definition for the vehicle registration mark retention eligibility project")
  lazy val vrmRetentionRetainProject = settingKey[Project]("The project definition for the vehicle registration mark retention retain project")
  lazy val vrmAssignEligibilityProject = settingKey[Project]("The project definition for the vehicle registration mark assign eligibility project")
  lazy val vrmAssignFulfilProject = settingKey[Project]("The project definition for the vehicle registration mark assign fulfil project")
  lazy val bruteForceEnabled = settingKey[Boolean]("Weather or not to enable the stub BruteForce service")
  lazy val auditProject = settingKey[Project]("The project definition for the audit project")
  lazy val emailServiceProject = settingKey[Project]("The project definition for the email service project")
}
