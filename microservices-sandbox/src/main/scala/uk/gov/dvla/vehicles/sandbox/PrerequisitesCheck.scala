package uk.gov.dvla.vehicles.sandbox

import org.apache.commons.io.FilenameUtils
import Runner.{configLocation, ansibleRepoLocation, ansibleRepoName}
import SandboxSettings.webAppSecrets
import sbt.Keys.{baseDirectory, target}
import sbt.{Def, File, IO, ThisProject}
import scala.sys.process.Process

object PrerequisitesCheck {
  private final val GitBranch = "develop"
  private final val GeneratedConfigFolderKey = "SANDBOX_GENERATED_CONFIG_FOLDER"
  private final val AnsibleRepoGitUrlKey = "SANDBOX_ANSIBLE_REPO_GIT_URL"

  final val GeneratedConfigFolder: Option[String] = sys.props.get(GeneratedConfigFolderKey)
    .orElse(sys.env.get(GeneratedConfigFolderKey))

  private final val AnsibleRepoGitUrl: Option[String] = sys.props.get(AnsibleRepoGitUrlKey)
    .orElse(sys.env.get(AnsibleRepoGitUrlKey))

  private def colorise(ansiColor: String)(output: String) =
    s"$ansiColor$output${scala.Console.RESET}"

  lazy val prerequisitesCheck = Def.task {

    /**
      * If SANDBOX_GENERATED_CONFIG_FOLDER has been specified then we skip the cloning or updating of the
      * Ansible repository and use the config that should have been previously generated in the /opt directory.
      * If it has not been set then we will either do a fresh clone of the repository or will perform an update
      * if it has been previously cloned. In both cases we deal with the branch specified in the GitBranch constant.
      *
      * @param ansibleRepo the ansibleRepo directory in the target directory of the exemplar
      */
    def updateAnsibleRepo(ansibleRepo: File) {
      GeneratedConfigFolder.fold {
        // GeneratedConfigFolder has not been specified by the developer so pull down the latest from git
        val ansibleRepoLocalPath = ansibleRepo.getAbsolutePath

        if (new File(ansibleRepo, ".git").exists()) {
          val gitOptions = s"--work-tree $ansibleRepoLocalPath --git-dir $ansibleRepoLocalPath/.git"
          // If we find the .git directory inside the ansibleRepo then fetch the latest
          val gitFetchLatestCommit = s"git $gitOptions fetch --depth=1 origin $GitBranch"
          val gitResetToLatestCommit = s"git $gitOptions reset --hard origin/$GitBranch"
          println(colorise(scala.Console.YELLOW)(
            s"""Now going to update existing git repo with the following commands:
              |$gitFetchLatestCommit
              |$gitResetToLatestCommit""".stripMargin
          ))
          println(Process(gitFetchLatestCommit).!!<)
          println(Process(gitResetToLatestCommit).!!<)
        } else {
          // Otherwise we need to do a fresh git clone
          val gitClone = s"git clone --depth=1 -b $GitBranch ${AnsibleRepoGitUrl.get} $ansibleRepoLocalPath"
          println(colorise(scala.Console.YELLOW)(
            s"Now going to run a fresh git clone with the following command: $gitClone"
          ))
          println(Process(gitClone).!!<)
          println("done.")
        }
      } { generatedConfigFolder =>
        // GeneratedConfigFolder has been specified by the developer so log that no cloning
        // or updating of the Ansible repo will happen
        println(colorise(scala.Console.YELLOW)(
          s"Skipping cloning or updating of repo because $GeneratedConfigFolderKey has been set to $generatedConfigFolder"
        ))
      }
    }

    validatePrerequisites()
    updateAnsibleRepo(ansibleRepoLocation((target in ThisProject).value))
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
    * If the SANDBOX_GENERATED_CONFIG_FOLDER has not been set eg. it is completely missing then the sandbox will
    * need to connect to Git and clone the repo within the target directory of the web app which is running the
    * sandbox. In this scenario the following prerequisite checks are performed:
    * 1. validate the git client is installed
    * 2. validate SANDBOX_ANSIBLE_REPO_GIT_URL has been set eg. git@gitlab.preview-dvla.co.uk:dvla/ansible-dvla-playbooks.git
    * 3. validate we can ssh to the git host part of the SANDBOX_ANSIBLE_REPO_GIT_URL
    * eg. ssh -T git@gitlab.preview-dvla.co.uk
    *
    * If the SANDBOX_GENERATED_CONFIG_FOLDER has been set and points to a previously cloned repo, the following
    * checks are performed:
    * 1. validate that the specified folder exists
    */
  private def validatePrerequisites() {
    def validateGitIsInstalled() = {
      print(colorise(scala.Console.YELLOW)("Verifying git is installed..."))
      if (Process("git --version").! != 0) {
        println(colorise(scala.Console.RED)(
          """FAILED.
           |You don't have git installed. Please install git and try again""".stripMargin
        ))
        throw new Exception("You don't have git installed. Please install git and try again")
      }
    }

    def validateAnsibleRepoGitUrlKey() = {
      print(colorise(scala.Console.YELLOW)("Verifying $AnsibleRepoGitUrlKey is passed..."))
      AnsibleRepoGitUrl.fold {
        println(colorise(scala.Console.RED)(
          s"""FAILED.
            |$AnsibleRepoGitUrlKey" not set. Please set it either as jvm arg of sbt
            |-D$AnsibleRepoGitUrlKey='git@git-host:theAnsibleRepoProjectName'
            |or export it in the environment with export $AnsibleRepoGitUrlKey='git@git-host:theAnsibleRepoProjectName'""".stripMargin
        ))
        throw new Exception(s"""There is no "$AnsibleRepoGitUrlKey" set neither as env variable nor as JVM property""")
      } { secret => println(s"done set to $secret") }
    }

    def validateCanSshToGitHost() = {
      val hostPrefix = "git@"
      // git@gitlab.preview-dvla.co.uk:dvla/secret-vehicles-online.git -> gitlab.preview-dvla.co.uk
      val gitHost: Option[String] =
        AnsibleRepoGitUrl.map(url=> url.replace(hostPrefix, "").substring(0, url.indexOf(":") - hostPrefix.length))

      print(colorise(scala.Console.YELLOW)(s"Verifying there is ssh access to ${gitHost.get}..."))
      if (Process(s"ssh -T git@${gitHost.get}").! != 0) {
        println(colorise(scala.Console.RED)(
          s"""FAILED.
            |Cannot connect to git@${gitHost.get}. Please check your ssh connection to ${gitHost.get}.
            |You might need to import your public key to ${gitHost.get}""".stripMargin
        ))
        throw new Exception(s"Cannot connect to git@${gitHost.get}. Please check your ssh connection to ${gitHost.get}.")
      }
    }

    def verifyGeneratedConfigFolder(generatedConfigFolder: String) = {
      print(colorise(scala.Console.YELLOW)(
        s"""There is a config folder $GeneratedConfigFolderKey=$generatedConfigFolder defined to be used.
          |Verifying that $generatedConfigFolder exists and is set correctly...""".stripMargin
      ))

      generatedConfigFolder match {
        case folder if !new File(folder).exists() =>
          println(colorise(scala.Console.RED)(
            s"""FAILED.
              |The generated config folder $generatedConfigFolder doesn't exist""".stripMargin
          ))
          throw new Exception(s"The generated config folder $generatedConfigFolder doesn't exist")
        case folder if folder != "/opt" =>
          println(colorise(scala.Console.RED)(
            s"""FAILED.
              |The generated config folder $generatedConfigFolder is not set to /opt""".stripMargin
          ))
          val msg = s"The generated config folder is set to $generatedConfigFolder. " +
            "If you are going to set it, it must be set to /opt"
          throw new Exception(msg)
        case _ =>
          println("done.")
      }
    }

    GeneratedConfigFolder.fold {
      // Handles the case when the GeneratedConfigFolder is None eg. it has not been specified by the developer.
      // Therefore, the sandbox will need to connect to Git and clone the repo so here we verify the prerequisites
      // that will allow us to do this
      println(colorise(scala.Console.YELLOW)(
        s"$GeneratedConfigFolderKey has not been set so we will now verify we can " +
          "connect to the Git secret repo for later cloning..."
      ))
      validateGitIsInstalled()
      validateAnsibleRepoGitUrlKey()
      validateCanSshToGitHost()
    } { generatedConfigFolder =>
      // Handles the case when the generated config folder has been specified
      verifyGeneratedConfigFolder(generatedConfigFolder)
    }
  }

  private def generateConfigFiles(): Unit = {
    GeneratedConfigFolder.fold {
      val applyPlaybookCommand = s"./target/$ansibleRepoName/gapply " +
        s"-i target/$ansibleRepoName/inventory/sandbox target/$ansibleRepoName/sandbox-accept.yml -t sandbox -e accept=yes"
      println(colorise(scala.Console.YELLOW)(
        s"Now generating the config with the following command: $applyPlaybookCommand"
      ))
      println(Process(applyPlaybookCommand).!!) // Run the playbook
      println("done.")
    } { _ =>
      println(colorise(scala.Console.YELLOW)(
        s"Skipping the generate config files step because $GeneratedConfigFolderKey is set."
      ))
    }
  }

  private def deployWebAppSecrets(secretsFilename: String, configDir: File, projectBaseDir: File): Unit = {
    val targetFile = new File(projectBaseDir, "conf/" + FilenameUtils.getName(secretsFilename))

    if (!targetFile.getCanonicalFile.exists()) {
      // The secrets file is missing in the web apps conf directory so copy the generated file that
      // has been created under the opt directory into the conf directory
      val configFile = new File(configDir, secretsFilename)
      print(colorise(scala.Console.YELLOW)(s"Copying the web app secrets from $configFile to $targetFile..."))
      IO.copyFile(configFile, targetFile)
      println("done.")
    } else {
      println(colorise(scala.Console.YELLOW)(
        s"Web app secrets file $secretsFilename already exists - skipping deploy web app secrets step."
      ))
    }
  }
}
