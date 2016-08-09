package uk.gov.dvla.vehicles.sandbox

import sbt.Compile
import sbt.Def
import sbt.File
import sbt.Keys.baseDirectory
import sbt.Keys.classDirectory
import sbt.Keys.fullClasspath
import sbt.Keys.run
import sbt.Keys.target
import sbt.{Runtime, Test}
import sbt.ThisProject
import Runner.ConfigDetails
import Runner.ConfigOutput
import Runner.runJavaMain
import Runner.runProject
import Runner.runScalaMain
import Runner.configLocation
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
import SandboxSettings.vrmAssignEligibilityProject
import SandboxSettings.vrmAssignFulfilProject
import SandboxSettings.vrmRetentionEligibilityProject
import SandboxSettings.vrmRetentionRetainProject

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
        configLocation((target in ThisProject).value),
        "os-address-lookup/os-address-lookup.conf",
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

  val vehicleAndKeeperLookupClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(vehicleAndKeeperLookupProject.value)}
  val vehicleAndKeeperLookupClassDir = Def.settingDyn {classDirectory.in(Runtime).in(vehicleAndKeeperLookupProject.value)}
  lazy val runVehicleAndKeeperLookup = Def.task {
    runProject(
      vehicleAndKeeperLookupClassPath.value,
      Some(ConfigDetails(
        configLocation((target in ThisProject).value),
        "vehicle-and-keeper-lookup/vehicle-and-keeper-lookup.conf",
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
        configLocation((target in ThisProject).value),
        "vehicles-dispose-fulfil/vehicles-dispose-fulfil.conf",
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
        configLocation((target in ThisProject).value),
        "vehicles-acquire-fulfil/vehicles-acquire-fulfil.conf",
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
        configLocation((target in ThisProject).value),
        "payment-solve/payment-solve.conf",
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
        configLocation((target in ThisProject).value),
        "vrm-retention-eligibility/vrm-retention-eligibility.conf",
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
        configLocation((target in ThisProject).value),
        "vrm-retention-retain/vrm-retention-retain.conf",
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
        configLocation((target in ThisProject).value),
        "vrm-assign-eligibility/vrm-assign-eligibility.conf",
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
        configLocation((target in ThisProject).value),
        "vrm-assign-fulfil/vrm-assign-fulfil.conf",
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
    runProject(
      auditClassPath.value,
      Some(ConfigDetails(
        configLocation((target in ThisProject).value),
        "audit/audit.conf",
        Some(ConfigOutput(
          new File(auditClassDir.value, "audit.conf"),
          setServicePort(auditPort.value)
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
        configLocation((target in ThisProject).value),
        "email-service/email-service.conf",
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
    runProject(
      fullClasspath.in(Test).value,
      None,
      runScalaMain("play.core.server.NettyServer", Array((baseDirectory in ThisProject).value.getAbsolutePath)),
      // The Play framework classes are for some reason not part of the Runtime class path of the application.
      // This is perhaps because they get added to the class path not by putting deps but from the play plugin.
      // By doing some trial and error it appears the ClassLoader below does contain the play classes, so we are
      // using it instead of the default Bootstrap ClassLoader
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
      "openingTimeMinOfDay" -> "0",
      "closingTimeMinOfDay" -> "1439",
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
   * and only brute force in the legacy stub). Achieved using JVM system properties which supersede anything
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
