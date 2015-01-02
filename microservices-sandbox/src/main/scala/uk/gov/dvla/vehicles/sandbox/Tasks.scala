package uk.gov.dvla.vehicles.sandbox

import sbt._
import sbt.Keys._
import Runner._
import SandboxSettings._
import ProjectDefinitions._

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

  val legacyStubsClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(legacyStubsProject.value)}
  lazy val runLegacyStubs = Def.task {
    runProject(
      legacyStubsClassPath.value,
      None,
      runJavaMain("service.LegacyServicesRunner", Array(legacyServicesStubsPort.value.toString))
    )

    if (bruteForceEnabled.value) sys.props ++= Map(
      "bruteForcePrevention.enabled" -> "true",
      "bruteForcePrevention.baseUrl" -> s"http://localhost:${legacyServicesStubsPort.value}/demo/services"
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
                          (setServicePort(osAddressLookupPort.value)(properties))
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

  lazy val runAppAndMicroservices = Def.task {
    runAllMicroservices.value
    run.in(Compile).toTask("").value
  }

  val gatlingTestsClassPath = Def.taskDyn {fullClasspath.in(Runtime).in(gatlingTestsProject.value)}
  val gatlingTestsTargetDir = Def.settingDyn {target.in(gatlingTestsProject.value)}
  lazy val testGatling = Def.task {
    val gatlingSimulationClass = gatlingSimulation.value
    def extractVehiclesGatlingJar(toFolder: File) =
      gatlingTestsClassPath.value.find(
        _.data.toURI.toURL.toString.endsWith(s"vehicles-gatling-$VersionVehiclesGatling.jar")
      ).map { jar => IO.unzip(new File(jar.data.toURI.toURL.getFile), toFolder)}

    if (!gatlingSimulationClass.isEmpty) {
      val targetFolder = gatlingTestsTargetDir.value.getAbsolutePath
      val vehiclesGatlingExtractDir = new File(s"$targetFolder/gatlingJarExtract")
      IO.delete(vehiclesGatlingExtractDir)
      vehiclesGatlingExtractDir.mkdirs()
      extractVehiclesGatlingJar(vehiclesGatlingExtractDir)
      sys.props += "gatling.core.disableCompiler" -> "true"
      runProject(
        gatlingTestsClassPath.value,
        None,
        runJavaMain(
          mainClassName = "io.gatling.app.Gatling",
          args = Array(
            "--simulation", gatlingSimulation.value,
            "--data-folder", s"${vehiclesGatlingExtractDir.getAbsolutePath}/data",
            "--results-folder", s"$targetFolder/gatling",
            "--request-bodies-folder", s"$targetFolder/request-bodies"
          ),
          method = "runGatling"
        )
      ) match {
        case 0 => println("Gatling execution SUCCESS")
        case exitCode =>
          println("Gatling execution FAILURE")
          throw new Exception(s"Gatling run exited with error $exitCode")
      }
    }
  }

  lazy val runAsync = Def.task {
    runAsyncHttpsEnvVars.value
    runProject(
      fullClasspath.in(Test).value,
      None,
      runScalaMain("play.core.server.NettyServer", Array((baseDirectory in ThisProject).value.getAbsolutePath))
    )
    sys.props += "acceptance.test.url" -> s"https://localhost:${httpsPort.value}/sell-to-the-trade/"
  }

  lazy val runAppAndMicroservicesAsync = Def.task[Unit] {
    runAllMicroservices.value
    runAsync.value
  }

  lazy val allAcceptanceTests = Def.task {
    acceptanceTests.value
    testGatling.value
  }

  lazy val runAsyncHttpsEnvVars = Def.task {
    val appContext = applicationContext.value match {
      case context: String if context.isEmpty() => ""
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
      "vrmAssignFulfilMicroServiceUrlBase" -> s"http://localhost:${vrmAssignFulfilPort.value}"
    )
  }
}
