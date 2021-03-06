package uk.gov.dvla.vehicles.sandbox

import com.typesafe.config.ConfigFactory
import java.io.StringReader
import java.net.{URL, URLClassLoader}
import org.apache.commons.io.FileUtils
import sbt.Scoped.{Apply2, Apply3}
import sbt.{Attributed, Def, File, ForkOptions, Task}
import PrerequisitesCheck.GeneratedConfigFolder
import scala.util.Properties.lineSeparator

object Runner {
  val ansibleRepoName = "ansibleRepo"
  type ITask[T]  = Def.Initialize[Task[T]]

  /**
    * Executes two task a and b in a sequence. B will start executing after a has finished.
    */
  def runSequentially[A, B](a: ITask[A], b: ITask[B]) =
    new Apply2((a, b)).apply((a, b) => a.flatMap(x => b))

  /**
    * Executes three task a and b and c in a sequence.
    * C will start executing after b has finished.
    * B will start executing after a has finished.
    */
  def runSequentially[A, B, C](a: ITask[A], b: ITask[B], c: ITask[C]) =
    new Apply3((a, b, c)).apply((a, b, c) => a.flatMap(x => b.flatMap(x => c)))

  /**
    * Returns a File object that points to the ansibleRepo sub directory in the given target folder
    *
    * @param targetFolder the target folder of a web app that is using the sandbox plugin
    * @return a File object that points to the ansibleRepo sub directory in the given target folder
    */
  def ansibleRepoLocation(targetFolder: File): File =
    new File(targetFolder, ansibleRepoName)

  /**
    * Returns the directory that contains the micro service and web app configuration for the sandbox
    * If the GeneratedConfigFolder is specified then the sandbox looks for the config in this directory,
    * which will be /opt.
    * However, if the GeneratedConfigFolder is not specified then the sandbox looks for the config in
    * the web app target/opt directory.
    *
    * @param targetFolder the web app target directory
    * @return the directory that contains the micro service and web app configuration files
    */
  def configLocation(targetFolder: File): File =
    GeneratedConfigFolder.fold {
      new File(targetFolder, "opt")
    } { generatedConfigFolder =>
      new File(generatedConfigFolder)
    }

  /**
    * Executes a piece of code with the passed class loader set as a context class loader
    *
    * @param classLoader the class loader to be used as a context class loader
    * @param code to be executed
    */
  def withClassLoader[T](classLoader: ClassLoader)(code: => T) {
    val currentContextClassLoader = Thread.currentThread().getContextClassLoader
    Thread.currentThread().setContextClassLoader(classLoader)
    try code
    finally Thread.currentThread().setContextClassLoader(currentContextClassLoader)
  }

  /**
    * Executes a Scala App class with arguments within the passed class loader.
    *
    * @param mainClassName the main class full path
    * @param args the arguments to be passed to the main method
    * @param method the static method to be executed. The default is "main"
    * @param prjClassLoader the class loader to be used to instantiate the main class.
    *                       Will be used as a context class loader as well.
    */
  def runScalaMain(mainClassName: String, args: Array[String] = Array[String](), method: String = "main")
                  (prjClassLoader: ClassLoader): Any = withClassLoader[Any](prjClassLoader) {
    // Scala reflection is not thread safe so have to lock here
    this.synchronized {
      import scala.reflect.runtime.universe.{newTermName, runtimeMirror}
      lazy val mirror = runtimeMirror(prjClassLoader)
      val bootSymbol = mirror.staticModule(mainClassName).asModule
      val boot = mirror.reflectModule(bootSymbol).instance
      val mainMethodSymbol = bootSymbol.typeSignature.member(newTermName(method)).asMethod
      val bootMirror = mirror.reflect(boot)
      bootMirror.reflectMethod(mainMethodSymbol).apply(args)
    }
  }

  /**
    * Executes a Java main class with arguments within the passed class loader.
    *
    * @param mainClassName the main class full path
    * @param args the arguments to be passed to the main method
    * @param method the static method to be executed. The default is "main"
    * @param prjClassLoader the class loader to be used to instantiate the main class.
    *                       Will be used as a context class loader as well.
    */
  def runJavaMain(mainClassName: String, args: Array[String] = Array[String](), method: String = "main")
                 (prjClassLoader: ClassLoader): Any = withClassLoader(prjClassLoader) {

    val mainClass = prjClassLoader.loadClass(mainClassName)
    val mainMethod = mainClass.getMethod(method, classOf[Array[String]])
    val mainResult = mainMethod.invoke(null, args)
    return mainResult
  }

  /**
    * Details used to extract the typesafe configuration file out of the secrets repository
    *
    * @param configDir the location of the Ansible config dir
    * @param config the path to the configuration that will have a transformation applied to it
    * @param output case class that contains the config file that will be transformed along with some transformation
    *               that will setup the config file for use in the sandbox
    *               By default no transformation is applied to the config.
    */
  case class ConfigDetails(configDir: File,
                           config: String,
                           output: Option[ConfigOutput])

  /**
    * Configuration about where to write a configuration along with an optional transform over it.
    *
    * @param transformedOutput the file to be written after applying the transformation function.
    * @param transform transform to apply to the config file so it will work in the sandbox. Could be used
    *                  to modify existing props, add new properties or delete properties.
    *                  Note that the default implementation is to do nothing.
    */
  case class ConfigOutput(transformedOutput: File, transform: String => String = s => s)

  /**
    * This method will transform the configuration for sandbox use, based on the given configDetails. It creates a new
    * class loader based on the given classpath and will call the given function with that newly created class loader.
    * The secrets will be within the classpath of the newly created class loader.
    *
    * @param prjClassPath the physical location of the jar files/folders on the file system (classpath elements)
    * @param configDetails handles the transformation of the config
    * @param runMainMethod the main method to call
    * @param parentClassLoader the ClassLoader to be used as a parent of the newly created ClassLoader
    *                          defined by @prjClassPath
    */
  def runProject(prjClassPath: Seq[Attributed[File]],
                 configDetails: Option[ConfigDetails],
                 runMainMethod: (ClassLoader) => Any = runJavaMain("dvla.microservice.Boot"),
                 parentClassLoader: ClassLoader =  ClassLoader.getSystemClassLoader.getParent): Unit = try {
    configDetails.foreach { case ConfigDetails(configDir, config, output) =>
      val configFile = new File(configDir, config)
      println(s"${scala.Console.YELLOW}Applying sandbox transformation to $configFile${scala.Console.RESET}")
      output.foreach { case ConfigOutput(outputToTransform, transform) =>
        copyAndTransform(configDir.getAbsolutePath, configFile, outputToTransform, transform)
      }
    }

    val prjClassloader = new URLClassLoader(
      prjClassPath.map(_.data.toURI.toURL).toArray,
      parentClassLoader
    )

    runMainMethod(prjClassloader)
  } catch {
    case t: Throwable =>
      t.printStackTrace()
      throw t
  }

  /** Run a forked JVM.
    *
    * @param config the options to pass to the forked JVM
    * @param classPath the physical location of the jar files/folders on the file system (classpath elements)
    * @param mainClass the main class to run
    */
  def runProjectForked(config: ForkOptions, classPath: Seq[File], mainClass: String): Unit = try {
    val scalaOptions = "-classpath" :: sbt.Path.makeString(classPath) :: mainClass :: Nil

    val process = sbt.Fork.java.fork(config, scalaOptions)
    lazy val shutdownHook = projectShutdownHook(process)
    Runtime.getRuntime.addShutdownHook(shutdownHook)

    def projectShutdownHook(process: sbt.Process): Thread = {
      new Thread(new Runnable {
        def run() {
          process.destroy()
        }
      })
    }
  } catch {
    case t: Throwable =>
      t.printStackTrace()
      throw t
  }

  /**
    * Takes a file that is in the Ansible generated config dir, which has already been created by running the
    * appropriate playbook applies a transformation function to it and writes it to the destination file that
    * is specified.
    *
    * @param ansibleConfigDir the location of the Ansible config dir
    * @param config path to the config file, which is relative to the ansible config dir
    * @param dest the location of the transformed file
    * @param transform a transformation to be applied to the config string before it's written.
    */
  def copyAndTransform(ansibleConfigDir: String, config: File, dest: File, transform: String => String) {
    if (!config.exists()) {
      throw new Exception(s"The config file does not exist - $config")
    }
    // Apply the transformation function to the contents of the file that the Ansible playbook has created
    // The transform function contains the logic for what to do to the String that is passed to it
    // in order to transform it. Remember this is may originally have been a curried function whose first argument list
    // has been supplied so we are now just dealing with a String => String function
    val transformedFile = transform(FileUtils.readFileToString(config))
    // Replace the contents with the new contents after they have been through the transformation
    FileUtils.writeStringToFile(dest, transformedFile)
    println(s"${scala.Console.YELLOW}Wrote transformed file contents to $dest${scala.Console.RESET}")
  }

  /**
   * Returns a transformation that adds the port configuration and edits the port of the urlProperty.
   * The urlProperty should be a valid URL
   *
   * @param servicePort the port property value
   * @param urlProperty a property key to a URL formatted string that will be set with a new port.
   * @param newPort the new port to be se to the URL property from above
   * @param properties the string representation of the configuration file.
   * @return
   */
  def setServicePortAndLegacyServicesPort(servicePort: Int, urlProperty: String, newPort: Int)
                                         (properties: String): String = {

    def updatePropertyPort(urlProperty: String, newPort: Int)(properties: String): String = {
      val config = ConfigFactory.parseReader(new StringReader(properties))
      val url = new URL(config.getString(urlProperty))
      val newUrl = new URL("http", "localhost", newPort, url.getFile).toString
      properties.replace(url.toString, newUrl.toString)
    }

    setServicePort(servicePort)(updatePropertyPort(urlProperty, newPort)(properties))
  }

  /**
   * Adds the service port to the end of the given set of properties in the format port = number
   *
   * @param servicePort the service port value
   * @param properties the existing properties
   * @return an updated set of properties that now includes the service port at the end
   */
  def setServicePort(servicePort: Int)(properties: String): String =
    s"""
       |$properties
       |port = $servicePort
    """.stripMargin

  /**
   * Sets a new value of a property within a string representation of the properties.
   * This is achieved by adding the new property at the start of the old properties and then
   * removing the old property from the old properties
 *
   * @param prop the property
   * @param value the property value
   * @param properties the string representation of the properties
   * @return
   */
  def substituteProp(prop: String, value: String)(properties: String): String =
    (s"""$prop = "$value"""" :: properties.lines
      .filterNot(_.contains(prop))
      .toList )
      .mkString(lineSeparator)
}
