package uk.gov.dvla.vehicles.sandbox

import sbt.Compile
import sbt.Def
import sbt.File
import sbt.Keys.baseDirectory
import sbt.Keys.classDirectory
import sbt.Keys.fullClasspath
import sbt.Keys.run
import sbt.Keys.target
import sbt.Runtime
import sbt.Test
import sbt.ThisProject
import Runner.ConfigDetails
import Runner.ConfigOutput
import Runner.runJavaMain
import Runner.runProject
import Runner.runScalaMain
import Runner.secretRepoLocation
import Runner.setServicePort
import Runner.setServicePortAndLegacyServicesPort
import Runner.substituteProp
import SandboxSettings.acceptanceTests
import SandboxSettings.applicationContext
import SandboxSettings.auditProject
import SandboxSettings.bruteForceEnabled
import SandboxSettings.emailServiceProject
import SandboxSettings.legacyStubsProject
import SandboxSettings.loadTests
import SandboxSettings.osAddressLookupProject
import SandboxSettings.paymentSolveProject
import SandboxSettings.portOffset
import SandboxSettings.runAllMicroservices
import SandboxSettings.vehicleAndKeeperLookupProject
import SandboxSettings.vehiclesAcquireFulfilProject
import SandboxSettings.vehiclesDisposeFulfilProject
import SandboxSettings.vehiclesLookupProject
import SandboxSettings.vrmAssignEligibilityProject
import SandboxSettings.vrmAssignFulfilProject
import SandboxSettings.vrmRetentionEligibilityProject
import SandboxSettings.vrmRetentionRetainProject

import scala.util.Properties.lineSeparator

object Tasks {
  private val httpsPort = Def.task(portOffset.value + 443)
  private val osAddressLookupPort = Def.task(portOffset.value + 801)
  private val vehicleLookupPort = Def.task(portOffset.value + 802)
  private val vehicleDisposePort = Def.task(portOffset.value + 803)
  private val vehiclesAcquireFulfilPort = Def.task(portOffset.value + 804)
  private val legacyServicesStubsPort = Def.task(portOffset.value + 806)
  private val vehicleAndKeeperLookupPort = Def.task(portOffset.value + 807)
  private val paymentSolvePort = Def.task(portOffset.value + 808)
  private val vrmRetentionEligibilityPort = Def.task(portOffset.value + 809)
  private val vrmRetentionRetainPort = Def.task(portOffset.value + 810)
  private val vrmAssignEligibilityPort = Def.task(portOffset.value + 811)
  private val vrmAssignFulfilPort = Def.task(portOffset.value + 812)
  private val auditPort = Def.task(portOffset.value + 813)
  private val emailServicePort = Def.task(portOffset.value + 814)

  val legacyStubsClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(legacyStubsProject.value)}
  lazy val runLegacyStubs = Def.task {
    runProject(
      legacyStubsClassPath.value,
      None,
      runJavaMain("service.LegacyServicesRunner", Array(legacyServicesStubsPort.value.toString))
    )
  }

  val osAddressLookupClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(osAddressLookupProject.value)}
  val osAddressLookupClassDir = Def.settingDyn {classDirectory.in(Runtime).in(osAddressLookupProject.value)}
  lazy val runOsAddressLookup = Def.task {
    runProject(
      osAddressLookupClassPath.value,
      Some(ConfigDetails(
        secretRepoLocation((target in ThisProject).value),
        "ms/dev/os-address-lookup.conf.enc",
        Some(ConfigOutput(
          new File(osAddressLookupClassDir.value, "os-address-lookup.conf"),
          properties =>
            substituteProp("ordnancesurvey.requesttimeout", "30000")
              (setServicePortAndLegacyServicesPort(
                osAddressLookupPort.value,
                "ordnancesurvey.preproduction.baseurl",
                legacyServicesStubsPort.value
              )(properties))
        ))
      ))
    )
  }

  val vehiclesLookupClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(vehiclesLookupProject.value)}
  val vehiclesLookupClassDir = Def.settingDyn {classDirectory.in(Runtime).in(vehiclesLookupProject.value)}
  lazy val runVehiclesLookup = Def.task {
    runProject(
      vehiclesLookupClassPath.value,
      Some(ConfigDetails(
        secretRepoLocation((target in ThisProject).value),
        "ms/dev/vehicles-lookup.conf.enc",
        Some(ConfigOutput(
          new File(vehiclesLookupClassDir.value, "vehicles-lookup.conf"),
          setServicePortAndLegacyServicesPort(
            vehicleLookupPort.value,
            "getVehicleDetails.baseurl",
            legacyServicesStubsPort.value
          )
        ))
      ))
    )
  }

  val vehicleAndKeeperLookupClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(vehicleAndKeeperLookupProject.value)}
  val vehicleAndKeeperLookupClassDir = Def.settingDyn {classDirectory.in(Runtime).in(vehicleAndKeeperLookupProject.value)}
  lazy val runVehicleAndKeeperLookup = Def.task {
    runProject(
      vehicleAndKeeperLookupClassPath.value,
      Some(ConfigDetails(
        secretRepoLocation((target in ThisProject).value),
        "ms/dev/vehicle-and-keeper-lookup.conf.enc",
        Some(ConfigOutput(
          new File(vehicleAndKeeperLookupClassDir.value, "vehicle-and-keeper-lookup.conf"),
          setServicePortAndLegacyServicesPort(
            vehicleAndKeeperLookupPort.value,
            "getVehicleAndKeeperDetails.baseurl",
            legacyServicesStubsPort.value
          )
        ))
      ))
    )
  }

  val vehiclesDisposeFulfilClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(vehiclesDisposeFulfilProject.value)}
  val vehiclesDisposeFulfilDir = Def.settingDyn {classDirectory.in(Runtime).in(vehiclesDisposeFulfilProject.value)}
  lazy val runVehiclesDisposeFulfil = Def.task {
    runProject(
      vehiclesDisposeFulfilClassPath.value,
      Some(ConfigDetails(
        secretRepoLocation((target in ThisProject).value),
        "ms/dev/vehicles-dispose-fulfil.conf.enc",
        Some(ConfigOutput(
          new File(vehiclesDisposeFulfilDir.value, "vehicles-dispose-fulfil.conf"),
          setServicePortAndLegacyServicesPort(
            vehicleDisposePort.value,
            "vss.baseurl",
            legacyServicesStubsPort.value
          )
        ))
      ))
    )
  }

  val vehiclesAcquireFulfilClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(vehiclesAcquireFulfilProject.value)}
  val vehiclesAcquireFulfilDir = Def.settingDyn {classDirectory.in(Runtime).in(vehiclesAcquireFulfilProject.value)}
  lazy val runVehiclesAcquireFulfil = Def.task {
    runProject(
      vehiclesAcquireFulfilClassPath.value,
      Some(ConfigDetails(
        secretRepoLocation((target in ThisProject).value),
        "ms/dev/vehicles-acquire-fulfil.conf.enc",
        Some(ConfigOutput(
          new File(vehiclesAcquireFulfilDir.value, "vehicles-acquire-fulfil.conf"),
          setServicePortAndLegacyServicesPort(
            vehiclesAcquireFulfilPort.value,
            "vss.baseurl",
            legacyServicesStubsPort.value
          )
        ))
      ))
    )
  }

  val paymentSolveClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(paymentSolveProject.value)}
  val paymentSolveClassDir = Def.settingDyn {classDirectory.in(Runtime).in(paymentSolveProject.value)}
  lazy val runPaymentSolve = Def.task {
    runProject(
      paymentSolveClassPath.value,
      Some(ConfigDetails(
        secretRepoLocation((target in ThisProject).value),
        "ms/dev/payment-solve.conf.enc",
        Some(ConfigOutput(
          new File(paymentSolveClassDir.value, "payment-solve.conf"),
          setServicePort(paymentSolvePort.value)
        ))
      ))
    )
  }

  val vrmRetentionEligibilityClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(vrmRetentionEligibilityProject.value)}
  val vrmRetentionEligibilityClassDir = Def.settingDyn {classDirectory.in(Runtime).in(vrmRetentionEligibilityProject.value)}
  lazy val runVrmRetentionEligibility = Def.task {
    runProject(
      vrmRetentionEligibilityClassPath.value,
      Some(ConfigDetails(
        secretRepoLocation((target in ThisProject).value),
        "ms/dev/vrm-retention-eligibility.conf.enc",
        Some(ConfigOutput(
          new File(vrmRetentionEligibilityClassDir.value, "vrm-retention-eligibility.conf"),
          setServicePortAndLegacyServicesPort(
            vrmRetentionEligibilityPort.value,
            "validateRetain.url",
            legacyServicesStubsPort.value
          )
        ))
      ))
    )
  }

  val vrmRetentionRetainClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(vrmRetentionRetainProject.value)}
  val vrmRetentionRetainClassDir = Def.settingDyn {classDirectory.in(Runtime).in(vrmRetentionRetainProject.value)}
  lazy val runVrmRetentionRetain = Def.task {
    runProject(
      vrmRetentionRetainClassPath.value,
      Some(ConfigDetails(
        secretRepoLocation((target in ThisProject).value),
        "ms/dev/vrm-retention-retain.conf.enc",
        Some(ConfigOutput(
          new File(vrmRetentionRetainClassDir.value, "vrm-retention-retain.conf"),
          setServicePortAndLegacyServicesPort(
            vrmRetentionRetainPort.value,
            "retain.url",
            legacyServicesStubsPort.value
          )
        ))
      ))
    )
  }

  val vrmAssignEligibilityClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(vrmAssignEligibilityProject.value)}
  val vrmAssignEligibilityClassDir = Def.settingDyn {classDirectory.in(Runtime).in(vrmAssignEligibilityProject.value)}
  lazy val runVrmAssignEligibility = Def.task {
    runProject(
      vrmAssignEligibilityClassPath.value,
      Some(ConfigDetails(
        secretRepoLocation((target in ThisProject).value),
        "ms/dev/vrm-assign-eligibility.conf.enc",
        Some(ConfigOutput(
          new File(vrmAssignEligibilityClassDir.value, "vrm-assign-eligibility.conf"),
          setServicePortAndLegacyServicesPort(
            vrmAssignEligibilityPort.value,
            "validateAssign.url",
            legacyServicesStubsPort.value
          )
        ))
      ))
    )
  }

  val vrmAssignFulfilClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(vrmAssignFulfilProject.value)}
  val vrmAssignFulfilClassDir = Def.settingDyn {classDirectory.in(Runtime).in(vrmAssignFulfilProject.value)}
  lazy val runVrmAssignFulfil = Def.task {
    runProject(
      vrmAssignFulfilClassPath.value,
      Some(ConfigDetails(
        secretRepoLocation((target in ThisProject).value),
        "ms/dev/vrm-assign-fulfil.conf.enc",
        Some(ConfigOutput(
          new File(vrmAssignFulfilClassDir.value, "vrm-assign-fulfil.conf"),
          setServicePortAndLegacyServicesPort(
            vrmAssignFulfilPort.value,
            "assignFulfil.url",
            legacyServicesStubsPort.value
          )
        ))
      ))
    )
  }

  val auditClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(auditProject.value)}
  val auditClassDir = Def.settingDyn {classDirectory.in(Runtime).in(auditProject.value)}
  lazy val runAudit = Def.task {
    def setAuditPort(servicePort: Int)(properties: String): String =
      (s"audit-port = $servicePort" :: properties.lines
        .filterNot(_.contains("audit-port"))
        .toList )
        .mkString(lineSeparator)

    runProject(
      auditClassPath.value,
      Some(ConfigDetails(
        secretRepoLocation((target in ThisProject).value),
        "ms/dev/audit.conf.enc",
        Some(ConfigOutput(
          new File(auditClassDir.value, "audit.conf"),
          setAuditPort(auditPort.value)
        ))
      ))
    )
  }

  val emailServiceClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(emailServiceProject.value)}
  val emailServiceClassDir = Def.settingDyn {classDirectory.in(Runtime).in(emailServiceProject.value)}
  lazy val runEmailService = Def.task {
    runProject(
      emailServiceClassPath.value,
      Some(ConfigDetails(
        secretRepoLocation((target in ThisProject).value),
        "ms/dev/email-service.conf.enc",
        Some(ConfigOutput(
          new File(emailServiceClassDir.value, "email-service.conf"),
          setServicePort(emailServicePort.value)
        ))
      ))
    )
  }

  lazy val runAppAndMicroservices = Def.task {
    runAllMicroservices.value
    run.in(Compile).toTask("").value
  }

  lazy val runAsync = Def.task {
    runAsyncHttpsEnvVars.value
    println(fullClasspath.in(Runtime).value.map(_.data.toURI.toURL.toString).sorted.mkString("\n"))
    runProject(
      fullClasspath.in(Test).value,
      None,
      runScalaMain("play.core.server.NettyServer", Array((baseDirectory in ThisProject).value.getAbsolutePath)),
      getClass.getClassLoader.getParent.getParent
    )
    sys.props += "acceptance.test.url" -> s"https://localhost:${httpsPort.value}/sell-to-the-trade/"
  }

  lazy val runAppAndMicroservicesAsync = Def.task[Unit] {
    runAllMicroservices.value
    runAsync.value
  }

  lazy val allAcceptanceTests = Def.task {
    acceptanceTests.value
    loadTests.value
  }

  lazy val runAsyncHttpsEnvVars = Def.task {
    val appContext = applicationContext.value match {
      case context: String if context.isEmpty => ""
      case context: String => s"/$context"
    }
    sys.props ++= Map(
      "openingTime" -> "0",
      "closingTime" -> "24",
      "https.port" -> httpsPort.value.toString,
      "http.port" -> "disabled",
      "jsse.enableSNIExtension" -> "false", // Disable the SNI for testing
      "baseUrl" -> s"https://localhost:${httpsPort.value}$appContext",
      "test.url" -> s"https://localhost:${httpsPort.value}$appContext/",
      "test.remote" -> "true",
      "bruteForcePrevention.enabled" -> "false"
    )
    if (bruteForceEnabled.value) sys.props ++= Map(
      "bruteForcePrevention.enabled" -> "true",
      "bruteForcePrevention.baseUrl" -> s"http://localhost:${legacyServicesStubsPort.value}/demo/services"
    )
  }

  /**
   * Overrides the properties that the exemplars use to connect to their external dependencies (micro services
   * and only brute force in the legacy stub). Achieved using JVM system properties which supercede anything
   * explicitly defined in the configuration files
   */
  val setMicroservicesPortsEnvVars = Def.task {
    sys.props ++= Map(
      "ordnancesurvey.baseUrl" -> s"http://localhost:${osAddressLookupPort.value}",
      "vehicleLookup.baseUrl" -> s"http://localhost:${vehicleLookupPort.value}",
      "vehicleAndKeeperLookupMicroServiceUrlBase" -> s"http://localhost:${vehicleAndKeeperLookupPort.value}",
      "disposeVehicle.baseUrl" -> s"http://localhost:${vehicleDisposePort.value}",
      "acquireVehicle.baseUrl" -> s"http://localhost:${vehiclesAcquireFulfilPort.value}",
      "paymentSolveMicroServiceUrlBase" -> s"http://localhost:${paymentSolvePort.value}",
      "vrmRetentionEligibilityMicroServiceUrlBase" -> s"http://localhost:${vrmRetentionEligibilityPort.value}",
      "vrmRetentionRetainMicroServiceUrlBase" -> s"http://localhost:${vrmRetentionRetainPort.value}",
      "vrmAssignEligibilityMicroServiceUrlBase" -> s"http://localhost:${vrmAssignEligibilityPort.value}",
      "vrmAssignFulfilMicroServiceUrlBase" -> s"http://localhost:${vrmAssignFulfilPort.value}",
      "emailServiceMicroServiceUrlBase" -> s"http://localhost:${emailServicePort.value}",
      "auditMicroServiceUrlBase" -> s"http://localhost:${auditPort.value}"
    )
    if (bruteForceEnabled.value) sys.props ++= Map(
      "bruteForcePrevention.enabled" -> "true",
      "bruteForcePrevention.baseUrl" -> s"http://localhost:${legacyServicesStubsPort.value}/demo/services"
    )
  }
}
