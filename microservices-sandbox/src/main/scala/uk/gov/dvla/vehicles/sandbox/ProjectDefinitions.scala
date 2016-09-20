package uk.gov.dvla.vehicles.sandbox

import sbt.file
import sbt.Keys.{libraryDependencies, resolvers, scalaVersion}
import sbt.ModuleID
import sbt.Project
import sbt.Resolver
import sbt.toGroupID
import sbt.toRepositoryName

/**
 * Helper methods to define the various microservices projects that a sandbox config might need.
 * All the defined projects will be preset with the dependencies passed to the project as well as with
 * some default ivy repositories.
 */
object ProjectDefinitions {
  final val VersionGatlingApp = "2.0.0-M4-NAP"
  final val VersionVehiclesGatling = "1.0-SNAPSHOT"

  final val scalaVersionStr = "2.11.8"

  private val nexus = "http://rep002-01.skyscape.preview-dvla.co.uk:8081/nexus/content/repositories"
  private val projectResolvers = Seq(
    "typesafe repo" at "http://repo.typesafe.com/typesafe/releases",
    "spray repo" at "http://repo.spray.io/",
    "local nexus snapshots" at s"$nexus/snapshots",
    "local nexus releases" at s"$nexus/releases"
  )

  def sandProject(name: String, deps: ModuleID*): Project =
    sandProject(name, Seq[Resolver](), deps: _*)

  def sandProject(name: String,
                  res: Seq[Resolver],
                  deps: ModuleID*): Project =
    Project(name, file(s"target/sandbox/$name"))
      .settings(libraryDependencies ++= deps)
      .settings(resolvers ++= (projectResolvers ++ res))

  private def microserviceProject(project: String,
                                  version: String,
                                  scalaVersionString: String) =
    sandProject(project, "dvla" %% s"$project" % version)
      .settings(scalaVersion := scalaVersionString)

  def osAddressLookup(version: String, scalaVersionString: String = scalaVersionStr) =
    microserviceProject("os-address-lookup", version, scalaVersionString = scalaVersionStr)

  def vehicleAndKeeperLookup(version: String, scalaVersionString: String = scalaVersionStr) =
    microserviceProject("vehicle-and-keeper-lookup", version, scalaVersionString = scalaVersionStr)

  def vehiclesDisposeFulfil(version: String, scalaVersionString: String = scalaVersionStr) =
    microserviceProject("vehicles-dispose-fulfil", version, scalaVersionString = scalaVersionStr)

  def vehiclesAcquireFulfil(version: String, scalaVersionString: String = scalaVersionStr) =
    microserviceProject("vehicles-acquire-fulfil", version, scalaVersionString = scalaVersionStr)

  def paymentSolve(version: String, scalaVersionString: String = scalaVersionStr) =
    microserviceProject("payment-solve", version, scalaVersionString = scalaVersionStr)

  def vrmRetentionEligibility(version: String, scalaVersionString: String = scalaVersionStr) =
    microserviceProject("vrm-retention-eligibility", version, scalaVersionString = scalaVersionStr)

  def vrmRetentionRetain(version: String, scalaVersionString: String = scalaVersionStr) =
    microserviceProject("vrm-retention-retain", version, scalaVersionString = scalaVersionStr)

  def vrmAssignEligibility(version: String, scalaVersionString: String = scalaVersionStr) =
    microserviceProject("vrm-assign-eligibility", version, scalaVersionString = scalaVersionStr)

  def vrmAssignFulfil(version: String, scalaVersionString: String = scalaVersionStr) =
    microserviceProject("vrm-assign-fulfil", version, scalaVersionString = scalaVersionStr)

  def emailService(version: String, scalaVersionString: String = scalaVersionStr) =
    microserviceProject("email-service", version, scalaVersionString = scalaVersionStr)

  def audit(version: String, scalaVersionString: String = scalaVersionStr) =
    microserviceProject("audit", version, scalaVersionString = scalaVersionStr)

  def legacyStubs(version: String) = sandProject(
    "legacy-stubs",
    "dvla-legacy-stub-services" % "legacy-stub-services-service" % version
  )

  def gatlingTests() = sandProject(
    name = "gatling",
    Seq("Central Maven" at "http://central.maven.org/maven2"),
    "com.netaporter.gatling" % "gatling-app" % VersionGatlingApp,
    "uk.gov.dvla" % "vehicles-gatling" % VersionVehiclesGatling
  )
}
