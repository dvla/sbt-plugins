package uk.gov.dvla.vehicles.sandbox

import org.apache.commons.io.{FilenameUtils, FileUtils}
import Runner.{decryptFile, decryptPassword, secretProperty, secretRepoLocation}
import SandboxSettings.webAppSecrets
import sbt.Keys.{baseDirectory, target}
import sbt.{Def, File, IO, ThisProject}
import scala.sys.process.Process

object PrerequisitesCheck {
  private final val GitBranch = "develop"
  private final val SecretRepoOfflineFolderKey = "SANDBOX_OFFLINE_SECRET_REPO_FOLDER"
  private final val SecretRepoGitUrlKey = "SANDBOX_SECRET_REPO_GIT_URL"
  private final val SecretRepoOfflineFolder: Option[String] = sys.props.get(SecretRepoOfflineFolderKey)
    .orElse(sys.env.get(SecretRepoOfflineFolderKey))
  private final val SecretRepoGitUrl: Option[String] = sys.props.get(SecretRepoGitUrlKey)
    .orElse(sys.env.get(SecretRepoGitUrlKey))

  lazy val prerequisitesCheck = Def.task {
    /**
     * If SANDBOX_OFFLINE_SECRET_REPO_FOLDER has been specified then delete the version inside the target
     * directory of the exemplar and replace it with the version specified in the env variable.
     * Otherwise look in the target directory of the exemplar for the secretRepo directory. If the repo
     * exists then we pull the develop branch otherwise we do a fresh git clone of the repo
     * @param secretRepo the secretRepo directory in the target directory of the exemplar
     */
    def updateSecretVehiclesOnline(secretRepo: File) {
      SecretRepoOfflineFolder.fold {
        // SecretRepoOfflineFolder has not been specified by the developer
        val secretRepoLocalPath = secretRepo.getAbsolutePath

        if (new File(secretRepo, ".git").exists()) {
          val gitOptions = s"--work-tree $secretRepoLocalPath --git-dir $secretRepoLocalPath/.git"
          // If we find the .git directory inside the secretRepo then we just pull the develop branch
          println(Process(s"git $gitOptions pull origin $GitBranch").!!<)
        } else
          // Otherwise we need to do a fresh git clone
          println(Process(s"git clone -b $GitBranch ${SecretRepoGitUrl.get} $secretRepoLocalPath").!!<)
      } { secretRepoOfflineFolder =>
        // SecretRepoOfflineFolder has been specified by the developer so delete the
        // version inside the target directory and replace it with the version specified
        // by the secretRepoOfflineFolder
        if (secretRepo.exists()) IO.delete(secretRepo)
        secretRepo.mkdirs()
        FileUtils.copyDirectory(new File(secretRepoOfflineFolder), secretRepo)
      }
    }

//    validatePrerequisites()
//    updateSecretVehiclesOnline(secretRepoLocation((target in ThisProject).value))
/*
    decryptWebAppSecrets(
      // Defined in the web app that is using the sandbox eg. ui/dev/vehiclesOnline.conf.enc
      // in dispose: SandboxSettings.webAppSecrets := "ui/dev/vehiclesOnline.conf.enc"
      webAppSecrets.value,
      // The base directory of the web app using the sandbox eg. /Users/ianstainer/dev/dvla/vehicles-online
      baseDirectory.in(ThisProject).value,
      // The location of the secret repo in the target directory eg. /Users/ianstainer/dev/dvla/vehicles-online/target/secretRepo
      secretRepoLocation(target.in(ThisProject).value)
    )
*/
  }

  /**
    * If the SANDBOX_OFFLINE_SECRET_REPO_FOLDER has not been set eg. it is completely missing then the sandbox will
    * need to connect to Git and clone the repo within the target directory of the web app which is running the
    * sandbox. In this scenario the following prerequisite checks are performed:
    * 1. validate the git client is installed
    * 2. validate SANDBOX_SECRET_REPO_GIT_URL has been set eg. git@gitlab.preview-dvla.co.uk:dvla/secret-vehicles-online.git
    * 3. validate we can ssh to the git host part of the SANDBOX_SECRET_REPO_GIT_URL
    * eg. ssh -T git@gitlab.preview-dvla.co.uk
    *
    * If the SANDBOX_OFFLINE_SECRET_REPO_FOLDER has been set and points to a previously cloned repo, the following
    * checks are performed:
    * 1. validate that the specified folder exists
    *
    * In both cases the final check is to verify that either DECRYPT_PASSWORD or GIT_SECRET_PASSPHRASE have been set
    * which is needed when decrypting encrypted files in the secret repo
    */
  private def validatePrerequisites() {
    def validateGitIsInstalled() = {
      print(s"${scala.Console.YELLOW}Verifying git is installed...${scala.Console.RESET}")
      if (Process("git --version").! != 0) {
        println(s"${scala.Console.RED}FAILED.")
        println(s"You don't have git installed. Please install git and try again${scala.Console.RESET}")
        throw new Exception("You don't have git installed. Please install git and try again")
      }
    }

    def validateSecretRepoGitUrlKey() = {
      print(s"${scala.Console.YELLOW}Verifying $SecretRepoGitUrlKey is passed...${scala.Console.RESET}")
      SecretRepoGitUrl.fold {
        println(s"""${scala.Console.RED}FAILED.${scala.Console.RESET}""")
        println(s"""${scala.Console.RED}"$SecretRepoGitUrlKey" not set. Please set it either as jvm arg of sbt """
          + s""" "-D$SecretRepoGitUrlKey='git@git-host:theSecretRepoProjectName'""""
          + s" or export it in the environment with export $SecretRepoGitUrlKey='git@git-host:theSecretRepoProjectName'"
          + s" ${scala.Console.RESET}")
        throw new Exception(s""" There is no "$SecretRepoGitUrlKey" set neither as env variable nor as JVM property """)
      } { secret => println(s"done set to $secret") }
    }

    def validateCanSshToGitHost() = {
      val hostPrefix = "git@"
      // git@gitlab.preview-dvla.co.uk:dvla/secret-vehicles-online.git -> gitlab.preview-dvla.co.uk
      val gitHost: Option[String] =
        SecretRepoGitUrl.map(url=> url.replace(hostPrefix, "").substring(0, url.indexOf(":") - hostPrefix.length))

      print(s"${scala.Console.YELLOW}Verifying there is ssh access to ${gitHost.get}...${scala.Console.RESET}")
      if (Process(s"ssh -T git@${gitHost.get}").! != 0) {
        println(s"${scala.Console.RED}FAILED.")
        println(s"Cannot connect to git@${gitHost.get}. Please check your ssh connection to ${gitHost.get}. "
          + s"You might need to import your public key to ${gitHost.get}${scala.Console.RESET}")
        throw new Exception(s"Cannot connect to git@${gitHost.get}. Please check your ssh connection to ${gitHost.get}.")
      }
    }

    def verifySecretRepoOfflineFolder(secretRepoOfflineFolder: String) = {
      println(s"${scala.Console.YELLOW}There is an offline folder $SecretRepoOfflineFolderKey=$secretRepoOfflineFolder"
        + s" defined to be used as a secret repo.${scala.Console.RESET}")
      print(s"${scala.Console.YELLOW}Verifying that $secretRepoOfflineFolder exists...${scala.Console.RESET}")

      if (!new File(secretRepoOfflineFolder).exists()) {
        println(s"${scala.Console.RED}FAILED.")
        println(s"The offline secret repo folder $secretRepoOfflineFolder doesn't exist${scala.Console.RESET}")
        throw new Exception(s"The offline secret repo folder $secretRepoOfflineFolder doesn't exist")
      } else {
        println("done")
      }
    }

    def validateGitDecryptPassword() = {
      print(s"${scala.Console.YELLOW}Verifying $secretProperty is set...${scala.Console.RESET}")
      decryptPassword.fold {
        println(s"""${scala.Console.RED}FAILED.${scala.Console.RESET}""")
        println(s"""${scala.Console.RED}"$secretProperty" not set. Please set it either as jvm arg of sbt """ +
          s""" "-D$secretProperty='secret'"""" +
          s" or export it in the environment with export $secretProperty='some secret prop' ${scala.Console.RESET}")
        throw new Exception(s""" There is no "$secretProperty" set neither as env variable nor as JVM property """)
      } { secret => println("done") }
    }

    SecretRepoOfflineFolder.fold {
      // Handles the case when the secretRepoOfflineFolder is None eg. it has not been specified by the developer.
      // Therefore, the sandbox will need to connect to Git and clone the repo so here we verify the prerequisites
      // that will allow us to do this
      println(s"${scala.Console.YELLOW}$SecretRepoOfflineFolderKey has not been set so we will now verify we can " +
        s"connect to the Git secret repo for later cloning...${scala.Console.RESET}")
      validateGitIsInstalled()
      validateSecretRepoGitUrlKey()
      validateCanSshToGitHost()
    } { secretRepoOfflineFolder =>
      // Handles the case when the secretRepoOfflineFolder has been specified
      verifySecretRepoOfflineFolder(secretRepoOfflineFolder)
    }
    validateGitDecryptPassword()
  }

  // If the unencrypted version of the web app's secrets file is missing in the conf directory this
  // method creates it. This means that you can checkout an exemplar and run "sbt sandbox"
  // (after setting up the appropriate environment variables) and everything should work. Alternatively,
  // if you need to update your unencrypted secrets to the latest version just delete the version in the
  // web app conf folder and run the sandbox. However, this does not create it as a symbolic link back to
  // the unencrypted file in the secrets repo, which is how it will usually be set up.
  private def decryptWebAppSecrets(encryptedFileName: String, projectBaseDir: File, sandboxSecretRepo: File): Unit = {
    val nonEncryptedFileName = encryptedFileName.substring(0, encryptedFileName.length - ".enc".length)
    val targetFile = new File(projectBaseDir, "conf/" + FilenameUtils.getName(nonEncryptedFileName))

    if (!targetFile.getCanonicalFile.exists()) {
      // The unencrypted secrets file is missing in the conf directory so decrypt
      // the encrypted file that is in target/secretRepo into the conf directory
      print(s"${scala.Console.YELLOW}Decrypting the secrets to $targetFile...${scala.Console.RESET}")
      val noop: (String) => String = a => a
      decryptFile(sandboxSecretRepo.getAbsolutePath,
        new File(sandboxSecretRepo, encryptedFileName),
        targetFile,
        noop
      )
      println("done")
    }
  }
}