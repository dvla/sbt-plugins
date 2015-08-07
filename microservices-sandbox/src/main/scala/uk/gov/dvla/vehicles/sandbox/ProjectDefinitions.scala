package uk.gov.dvla.vehicles.sandbox

import sbt.file
import sbt.Keys.{libraryDependencies, resolvers}
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

  def osAddressLookup(version: String) =
    sandProject("os-address-lookup", "dvla" %% "os-address-lookup" % version)

  def vehiclesLookup(version: String) =
    sandProject("vehicles-lookup", "dvla" %% "vehicles-lookup" % version)

  def vehicleAndKeeperLookup(version: String) =
    sandProject("vehicle-and-keeper-lookup", "dvla" %% "vehicle-and-keeper-lookup" % version)

  def vehiclesDisposeFulfil(version: String) =
    sandProject("vehicles-dispose-fulfil", "dvla" %% "vehicles-dispose-fulfil" % version)

  def vehiclesAcquireFulfil(version: String) =
    sandProject("vehicles-acquire-fulfil", "dvla" %% "vehicles-acquire-fulfil" % version)

  def paymentSolve(version: String) =
    sandProject("payment-solve", "dvla" %% "payment-solve" % version)

  def vrmRetentionEligibility(version: String) =
    sandProject("vrm-retention-eligibility", "dvla" %% "vrm-retention-eligibility" % version)

  def vrmRetentionRetain(version: String) =
    sandProject("vrm-retention-retain", "dvla" %% "vrm-retention-retain" % version)

  def vrmAssignEligibility(version: String) =
    sandProject("vrm-assign-eligibility", "dvla" %% "vrm-assign-eligibility" % version)

  def vrmAssignFulfil(version: String) =
    sandProject("vrm-assign-fulfil", "dvla" %% "vrm-assign-fulfil" % version)

  def emailService(version: String) =
    sandProject("email-service", "dvla" %% "email-service" % version)

  def audit(version: String) =
    sandProject("audit", "dvla" % "audit_2.11" % version)

  def legacyStubs(version: String) = sandProject(
    name = "legacy-stubs",
    "dvla-legacy-stub-services" % "legacy-stub-services-service" % version
  )

  def gatlingTests() = sandProject(
    name = "gatling",
    Seq("Central Maven" at "http://central.maven.org/maven2"),
    "com.netaporter.gatling" % "gatling-app" % VersionGatlingApp,
    "uk.gov.dvla" % "vehicles-gatling" % VersionVehiclesGatling
  )
}
