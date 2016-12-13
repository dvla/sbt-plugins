package uk.gov.dvla.vehicles.detailsgenerator

import java.io.File
import java.util.Date

import sbt.Keys._
import sbt.Scoped.Apply2
import sbt._
import scala.sys.process.Process
import scala.util.control.NonFatal

object BuildDetailGenerator extends AutoPlugin {
  override def trigger = allRequirements

  override def projectSettings = Seq(
    compile :=  runSequentially(saveBuildDetails, compile.in(ThisProject).in(Compile)).value,
    test := runSequentially(compile, test.in(ThisProject).in(Test)).value
  )

  def prop(name: String) = sys.props.getOrElse(name, "Unknown")
  def buildDetails(name: String, version: String, sbtVersion: String): String =
    s"""Name: $name
       |Version: $version
       |
       |Build on: ${new Date()}
       |Build by: ${prop("user.name")}@${java.net.InetAddress.getLocalHost.getHostName}
       |Build OS: ${prop("os.name")}-${prop("os.version")}
       |Build Java: ${prop("java.version")} ${prop("java.vendor")}
       |Build SBT: $sbtVersion
       |
       |$commits
    """.stripMargin

  def commits = {
    val gitRepoFolder = new File(".").getCanonicalPath
    val gitOptions = Seq("--work-tree", gitRepoFolder, "--git-dir", s"$gitRepoFolder/.git")
    val gitCommand = Seq("git") ++ gitOptions ++ Seq("log", """--pretty=format:%ai %H %s -- <%ar by %an> %d""", "-15")
    try Process(gitCommand).!!<
    catch { case NonFatal(t) => "Cannot fetch git history: \n" + t.getStackTraceString}
  }

  def saveBuildDetails = Def.task {
    val buildDetailsName = "build-details.txt"
    val buildDetailsFile = new File(classDirectory.in(ThisProject).in(Compile).value, buildDetailsName)
    IO.write(buildDetailsFile, buildDetails(name.in(ThisProject).value, version.in(ThisProject).value, sbtVersion.value))
    println(s"Build details written to: $buildDetailsFile \n ${buildDetails(name.in(ThisProject).value, version.in(ThisProject).value, sbtVersion.value)}")
    Seq((new File(resourceDirectory.in(ThisProject).in(Compile).value, buildDetailsName), buildDetailsFile))
  }

  private type ITask[T]  = Def.Initialize[Task[T]]

  private def runSequentially[A, B](a: ITask[A], b: ITask[B]) =
    new Apply2((a, b)).apply((a, b) => a.flatMap(x => b))
}
