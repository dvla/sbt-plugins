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
      System.setProperty("gatling.core.disableCompiler", "true")
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
    System.setProperty("acceptance.test.url", s"https://localhost:${httpsPort.value}/sell-to-the-trade/")
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
    System.setProperty("openingTime", "0")
    System.setProperty("closingTime", "24")
    System.setProperty("https.port", httpsPort.value.toString)
    System.setProperty("http.port", "disabled")
    System.setProperty("jsse.enableSNIExtension", "false") // Disable the SNI for testing
    System.setProperty("baseUrl", s"https://localhost:${httpsPort.value}$appContext")
    System.setProperty("test.url", s"https://localhost:${httpsPort.value}$appContext/")
    System.setProperty("test.remote", "true")
    System.setProperty("bruteForcePrevention.enabled", "false")
  }

  val setMicroservicesPortsEnvVars = Def.task {
    System.setProperty("ordnancesurvey.baseUrl", s"http://localhost:${osAddressLookupPort.value}")
    System.setProperty("vehicleLookup.baseUrl", s"http://localhost:${vehicleLookupPort.value}")
    System.setProperty("vehicleAndKeeperLookupMicroServiceUrlBase", s"http://localhost:${vehicleAndKeeperLookupPort.value}")
    System.setProperty("disposeVehicle.baseUrl", s"http://localhost:${vehicleDisposePort.value}")
    System.setProperty("acquireVehicle.baseUrl", s"http://localhost:${vehiclesAcquireFulfilPort.value}")
  }
}
