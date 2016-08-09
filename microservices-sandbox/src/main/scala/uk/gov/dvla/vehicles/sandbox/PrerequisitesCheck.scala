package uk.gov.dvla.vehicles.sandbox

import org.apache.commons.io.FilenameUtils
import Runner.{configLocation, secretRepoLocation}
import SandboxSettings.webAppSecrets
import sbt.Keys.{baseDirectory, target}
import sbt.{Def, File, IO, ThisProject}
import scala.sys.process.Process

object PrerequisitesCheck {
//  private final val GitBranch = "develop"
  // TODO change this back to develop branch once everything is merged
  private final val GitBranch = "include_secrets"
  private final val SecretRepoOfflineFolderKey = "SANDBOX_OFFLINE_SECRET_REPO_FOLDER"
  private final val SecretRepoGitUrlKey = "SANDBOX_SECRET_REPO_GIT_URL"

  final val SecretRepoOfflineFolder: Option[String] = sys.props.get(SecretRepoOfflineFolderKey)
    .orElse(sys.env.get(SecretRepoOfflineFolderKey))

  private final val SecretRepoGitUrl: Option[String] = sys.props.get(SecretRepoGitUrlKey)
    .orElse(sys.env.get(SecretRepoGitUrlKey))

  lazy val prerequisitesCheck = Def.task {

    /**
      * If SANDBOX_OFFLINE_SECRET_REPO_FOLDER has been specified then we skip the cloning or updating of the
      * Ansible repository and use the config that should have been previously generated in the /opt directory.
      * If it has not been set then we will either do a fresh clone of the repository or will perform an update
      * if it has been previously cloned. In both cases we deal with the branch specified in the GitBranch constant.
      *
      * @param secretRepo the secretRepo directory in the target directory of the exemplar
      */
    def updateSecretVehiclesOnline(secretRepo: File) {
      SecretRepoOfflineFolder.fold {
        // SecretRepoOfflineFolder has not been specified by the developer so pull down the latest from git
        val secretRepoLocalPath = secretRepo.getAbsolutePath

        if (new File(secretRepo, ".git").exists()) {
          val gitOptions = s"--work-tree $secretRepoLocalPath --git-dir $secretRepoLocalPath/.git"
          // If we find the .git directory inside the secretRepo then we just pull the develop branch
          println(s"${scala.Console.YELLOW}" +
            "Now going to update existing git repo with the following command: " +
            s"git $gitOptions pull origin $GitBranch" +
            s"${scala.Console.RESET}")
          println(Process(s"git $gitOptions pull origin $GitBranch").!!<)
        } else {
          // Otherwise we need to do a fresh git clone
          println(s"${scala.Console.YELLOW}" +
            "Now going to run a fresh git clone with the following command: " +
            s"git clone -b $GitBranch ${SecretRepoGitUrl.get} $secretRepoLocalPath" +
            s"${scala.Console.RESET}")
          println(Process(s"git clone -b $GitBranch ${SecretRepoGitUrl.get} $secretRepoLocalPath").!!<)
          println("done.")
        }
      } { secretRepoOfflineFolder =>
        // SecretRepoOfflineFolder has been specified by the developer so log that no cloning
        // or updating of the Ansible repo will happen
        println(s"${scala.Console.YELLOW}" +
          s"Skipping cloning or updating of repo because $SecretRepoOfflineFolderKey has been set to $secretRepoOfflineFolder" +
          s"${scala.Console.RESET}")
      }
    }

    validatePrerequisites()
    updateSecretVehiclesOnline(secretRepoLocation((target in ThisProject).value))
    generateConfigFiles()
    deployWebAppSecrets(
      // Defined in the web app that is using the sandbox eg. vehicles-online/conf/vehiclesOnline.conf
      // in dispose: SandboxSettings.webAppSecrets := "vehicles-online/conf/vehiclesOnline.conf"
      secretsFilename = webAppSecrets.value,
      // Where to find the config files
      configDir = configLocation((target in ThisProject).value),
      // The base directory of the web app using the sandbox eg. /Users/ianstainer/dev/dvla/vehicles-online
      projectBaseDir = baseDirectory.in(ThisProject).value
    )
  }

  /**
    * If the SANDBOX_OFFLINE_SECRET_REPO_FOLDER has not been set eg. it is completely missing then the sandbox will
    * need to connect to Git and clone the repo within the target directory of the web app which is running the
    * sandbox. In this scenario the following prerequisite checks are performed:
    * 1. validate the git client is installed
    * 2. validate SANDBOX_SECRET_REPO_GIT_URL has been set eg. git@gitlab.preview-dvla.co.uk:dvla/ansible-dvla-playbooks.git
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
      print(s"${scala.Console.YELLOW}Verifying that $secretRepoOfflineFolder exists and is set correctly...${scala.Console.RESET}")

      secretRepoOfflineFolder match {
        case folder if !new File(folder).exists() =>
          println(s"${scala.Console.RED}FAILED.")
          println(s"The offline secret repo folder $secretRepoOfflineFolder doesn't exist${scala.Console.RESET}")
          throw new Exception(s"The offline secret repo folder $secretRepoOfflineFolder doesn't exist")
        case folder if folder != "/opt" =>
          println(s"${scala.Console.RED}FAILED.")
          println(s"The offline secret repo folder $secretRepoOfflineFolder is not set to /opt${scala.Console.RESET}")
          val msg = s"The offline secret repo folder is set to $secretRepoOfflineFolder. " +
            "If you are going to set it, it must be set to /opt"
          throw new Exception(msg)
        case _ =>
          println("done.")
      }
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
  }

  private def generateConfigFiles(): Unit = {
    SecretRepoOfflineFolder.fold {
      val applyPlaybookCommand = "./target/secretRepo/gapply " +
        "-i target/secretRepo/inventory/sandbox target/secretRepo/sandbox-accept.yml -t sandbox -e accept=yes"
      println(s"${scala.Console.YELLOW}" +
        s"Now generating the config with the following command: $applyPlaybookCommand${scala.Console.RESET}")
      println(Process(applyPlaybookCommand).!!) // Run the playbook
      println("done.")
    } { _ =>
      println(s"${scala.Console.YELLOW}" +
        s"Skipping the generate config files step because $SecretRepoOfflineFolderKey is set." +
        s"${scala.Console.RESET}")
    }
  }

  private def deployWebAppSecrets(secretsFilename: String, configDir: File, projectBaseDir: File): Unit = {
    val targetFile = new File(projectBaseDir, "conf/" + FilenameUtils.getName(secretsFilename))

    if (!targetFile.getCanonicalFile.exists()) {
      // The secrets file is missing in the web apps conf directory so copy the generated file that
      // has been created under the opt directory into the conf directory
      val configFile = new File(configDir, secretsFilename)
      print(s"${scala.Console.YELLOW}Copying the web app secrets from $configFile to $targetFile...${scala.Console.RESET}")
      IO.copyFile(configFile, targetFile)
      println("done.")
    } else {
      println(s"${scala.Console.YELLOW}" +
        s"Web app secrets file $secretsFilename already exists - skipping deploy web app secrets step." +
        s"${scala.Console.RESET}")
    }
  }
}