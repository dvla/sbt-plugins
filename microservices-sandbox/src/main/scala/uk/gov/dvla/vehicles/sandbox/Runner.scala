package uk.gov.dvla.vehicles.sandbox

import com.typesafe.config.ConfigFactory
import java.io.StringReader
import java.net.{URL, URLClassLoader}
import org.apache.commons.io.FileUtils
import sbt.Scoped.{Apply2, Apply3}
import sbt.{Attributed, Def, File, IO, Task}
import scala.sys.process.Process
import scala.util.Properties.lineSeparator

object Runner {
  val secretProperty = "DECRYPT_PASSWORD"
  val secretProperty2 = "GIT_SECRET_PASSPHRASE"
  val decryptPassword: Option[String] = sys.props.get(secretProperty)
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
   * @param targetFolder the target folder of a web app that is using the sandbox plugin
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
    * @param decryptedConfigDir the location of the Ansible decrypted config dir
    * @param decryptedConfig the path to the decrypted configuration that will have a transformation applied to it
    * @param output case class that contains the config file that will be transformed along with some transformation
    *               that will setup the config file for use in the sandbox
    *               By default no transformation is applied to the decrypted config.
    */
  case class ConfigDetails(decryptedConfigDir: File,
                           decryptedConfig: String,
                           output: Option[ConfigOutput])

  /**
    * Configuration about where to write a decrypted configuration along with an optional transform over it.
    *
    * @param transformedOutput the file to be written after applying the transformation function.
    * @param transform transform to apply to the config file so it will work in the sandbox. Could be used
    *                  to modify existing props, add new properties or delete properties.
    *                  Note that the default implementation is to do nothing.
    */
  case class ConfigOutput(transformedOutput: File, transform: String => String = s => s)

  /**
   * This method will decrypt and transform the secrets, based on the given configDetails. It creates a new class
   * loader based on the given classpath and will call the given function with that newly created class loader.
   * The secrets will be within the classpath of the newly created class loader.
   * @param prjClassPath the physical location of the jar files/folders on the file system (classpath elements)
   * @param configDetails handles the decrypting/transformation of the secrets
   * @param runMainMethod the main method to call
   * @param parentClassLoader the ClassLoader to be used as a parent of the newly created ClassLoader
   *                          defined by @prjClassPath
   */
  def runProject(prjClassPath: Seq[Attributed[File]],
                 configDetails: Option[ConfigDetails],
                 runMainMethod: (ClassLoader) => Any = runJavaMain("dvla.microservice.Boot"),
                 parentClassLoader: ClassLoader =  ClassLoader.getSystemClassLoader.getParent): Unit = try {
    configDetails.foreach { case ConfigDetails(decryptedConfigDir, decryptedConfig, output) =>
      val decryptedConfigFile = new File(decryptedConfigDir, decryptedConfig)
      println(s"Applying sandbox transformation to $decryptedConfigFile")
      output.foreach { case ConfigOutput(decryptedOutput, transform) =>
        copyAndTransform(decryptedConfigDir.getAbsolutePath, decryptedConfigFile, decryptedOutput, transform)
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

  /**
    * Takes a file that is in the Ansible config dir, which has already been created by running the appropriate playbook
    * applies a transformation function to it and writes it to the destination file that is specified.
    *
    * @param decryptedConfigDir the location of the Ansible config dir
    * @param decryptedConfig path to the decrypted config file, which is relative to the decrypted config dir
    * @param dest the location of the transformed file
    * @param decryptedTransform a transformation to be applied to the decrypted config string before it's written.
    */
  def copyAndTransform(decryptedConfigDir: String, decryptedConfig: File, dest: File, decryptedTransform: String => String) {
    if (!decryptedConfig.exists()) {
      throw new Exception(s"The config file does not exist - $decryptedConfig")
    }
    // Apply the transformation function to the contents of the decrypted file that the Ansible playbook has created
    // The decryptedTransform function contains the logic for what to do to the String that is passed to it
    // in order to transform it. Remember this is may originally have been a curried function whose first argument list
    // has been supplied so we are now just dealing with a String => String function
    val transformedFile = decryptedTransform(FileUtils.readFileToString(decryptedConfig))
    // Replace the contents with the new contents after they have been through the transformation
    FileUtils.writeStringToFile(dest, transformedFile)
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
    val unencryptedFilePath = encrypted.getAbsolutePath.substring(0, encrypted.getAbsolutePath.length - ".enc".length)
    if (new File(unencryptedFilePath).exists()) {
      // Decrypted version of the file already exists in the secret repo so just copy it to the destination
      val unencryptedFileName = unencryptedFilePath
        .substring(unencryptedFilePath.lastIndexOf('/') + 1, unencryptedFilePath.length)
      // Need to lock here to match up the print and println as sbt runs different invocations of this method in parallel
      this.synchronized {
        print(s"${scala.Console.YELLOW}$unencryptedFileName exists in the sandbox secret repo so will " +
          s"copy it to $dest...${scala.Console.RESET}")
        IO.copyFile(new File(unencryptedFilePath), dest)
        println("done")
      }
    } else {
      val decryptFileBashScript = s"$sandboxSecretRepo/decrypt-file"
      Process(s"chmod +x $decryptFileBashScript").!!< // Make the bash script executable
      dest.getParentFile.mkdirs()
      if (!encrypted.exists()) throw new Exception(s"File to be decrypted ${encrypted.getAbsolutePath} doesn't exist!")

      val decryptCommand = s"$decryptFileBashScript ${encrypted.getAbsolutePath} ${dest.getAbsolutePath} ${decryptPassword.get}"
      Process(decryptCommand).!!<
    }
    // Apply the transformation function to the contents of the decrypted/copied file
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
