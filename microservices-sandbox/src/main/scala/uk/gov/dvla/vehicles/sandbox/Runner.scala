package uk.gov.dvla.vehicles.sandbox

import java.io.StringReader
import java.net.{URL, URLClassLoader}
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import sbt.Scoped.{Apply2, Apply3}
import sbt.{Attributed, Def, File, Task}
import scala.sys.process.Process
import scala.util.Properties.lineSeparator

object Runner {
  val secretProperty = "DECRYPT_PASSWORD"
  val secretProperty2 = "GIT_SECRET_PASSPHRASE"
  val decryptPassword = sys.props.get(secretProperty)
    .orElse(sys.env.get(secretProperty))
    .orElse(sys.props.get(secretProperty2))
    .orElse(sys.env.get(secretProperty2))

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
   * Returns a File object that points to the secretRepo sub directory in the given target folder
   * @param targetFolder the target folder of a web app tha is using the sandbox plugin
   * @return a File object that points to the secretRepo sub directory in the given target folder
   */
  def secretRepoLocation(targetFolder: File): File =
    new File(targetFolder, "secretRepo")

  /**
   * Executes a piece of code with the passed class loader set as a context class loader
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
   * @param mainClassName the main class full path
   * @param args the arguments to be passed to the main method
   * @param method the static method to be executed. The default is "main"
   * @param prjClassLoader the class loader to be used to instantiate the main class.
   *                       Will be used as a context class loader as well.
   */
  def runScalaMain(mainClassName: String, args: Array[String] = Array[String](), method: String = "main")
                  (prjClassLoader: ClassLoader): Any = withClassLoader[Any](prjClassLoader) {
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
   * @param secretRepo the location of the temp secret repo
   * @param encryptedConfig the path to the encrypted configuration that needs to be decrypted
   * @param output the decrypted file configuration along with some transformation e.g. adding extra properties.
   *               By default there is no transformation applied to the decrypted configuration.
   */
  case class ConfigDetails(secretRepo: File,
                           encryptedConfig: String,
                           output: Option[ConfigOutput])

  /**
   * Configuration about where to write a decrypted configuration along with an optional transform over it.
   * @param decryptedOutput the file to be written after decrypting a configuration file from secrets.
   * @param transform a transform to apply over the decrypted file. Could be used to modify existing props, add new
   *                  properties or delete properties. Note that the default implementation is to do nothing
   */
  case class ConfigOutput(decryptedOutput: File, transform: String => String = a => a)

  /**
   * TODO: provide a description of this method
   * @param prjClassPath
   * @param configDetails
   * @param runMainMethod
   * @return
   */
  def runProject(prjClassPath: Seq[Attributed[File]],
                 configDetails: Option[ConfigDetails],
                 runMainMethod: (ClassLoader) => Any = runJavaMain("dvla.microservice.Boot")): Any = try {
    configDetails.map { case ConfigDetails(secretRepo, encryptedConfig, output) =>
      val encryptedConfigFile = new File(secretRepo, encryptedConfig)
      output.map { case ConfigOutput(decryptedOutput, transform) =>
        decryptFile(secretRepo.getAbsolutePath, encryptedConfigFile, decryptedOutput, transform)
      }
    }

    val prjClassloader = new URLClassLoader(
      prjClassPath.map(_.data.toURI.toURL).toArray,
      getClass.getClassLoader.getParent.getParent
    )

    runMainMethod(prjClassloader)
  } catch {
    case t: Throwable =>
      t.printStackTrace()
      throw t
  }

  /**
   * Decrypts a file from the secret repo, transforms it if needed and writes it to a destination file passed.
   * Uses external executable decrypt-file bash script which should be located in the secrets repository.
   * @param sandboxSecretRepo the location of the secrets repository within the target directory
   * @param encrypted relative path based on the sandbox secretRepo location of a file to be decrypted
   * @param dest the decrypted file location
   * @param decryptedTransform a transformation to be applied to the decrypted string before it's written.
   */
  def decryptFile(sandboxSecretRepo: String, encrypted: File, dest: File, decryptedTransform: String => String) {
    val decryptFileBashScript = s"$sandboxSecretRepo/decrypt-file"
    Process(s"chmod +x $decryptFileBashScript").!!< // Make the bash script executable
    dest.getParentFile.mkdirs()
    if (!encrypted.exists()) throw new Exception(s"File to be decrypted ${encrypted.getAbsolutePath} doesn't exist!")
    val decryptCommand = s"$decryptFileBashScript ${encrypted.getAbsolutePath} ${dest.getAbsolutePath} ${decryptPassword.get}"

    Process(decryptCommand).!!<

    // Apply the transformation function to the contents of the decrypted file
    val transformedFile = decryptedTransform(FileUtils.readFileToString(dest))
    // Replace the contents with the new contents after they have been through the transformation
    FileUtils.writeStringToFile(dest, transformedFile)
  }

  /**
   * Returns a transformation that adds the port configuration and edits the port of the urlProperty.
   * The urlProperty should be a valid URL
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
