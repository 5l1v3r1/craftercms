/*
 * Copyright (C) 2007-2019 Crafter Software Corporation. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

@Grapes([
        @Grab(group = 'org.slf4j', module = 'slf4j-nop', version = '1.7.25'),
        @Grab(group = 'org.apache.commons', module = 'commons-lang3', version = '3.7'),
        @Grab(group = 'org.apache.commons', module = 'commons-collections4', version = '4.1'),
        @Grab(group = 'commons-codec', module = 'commons-codec', version = '1.11'),
        @Grab(group = 'commons-io', module = 'commons-io', version = '2.6'),
])

import groovy.transform.Field

import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils

import utils.NioUtils

import static java.nio.file.StandardCopyOption.*
import static utils.EnvironmentUtils.*
import static utils.ScriptUtils.*

// Files that should not be overwritten automatically
@Field def configFilePatterns = [
    'crafter-setenv\\.sh',
    'apache-tomcat/conf/.+',
    'apache-tomcat/shared/classes/.+',
    'crafter-deployer/config/.+',
    'crafter-deployer/logging\\.xml',
    'elasticsearch/config/.+',
    'solr/server/resources/.+',
    'solr/server/solr/[^/]+',
    'solr/server/solr/configsets/crafter_configs/.+'
]

@Field def backupTimestampFormat = "yyyyMMddHHmmss"

/**
 * Builds the CLI and adds the possible options
 */
def buildCli(cli) {
    cli.h(longOpt: 'help', 'Show usage information')
}

/**
 * Prints the help info
 */
def printHelp(cli) {
    cli.usage()
}

/**
 * Exits the script with an error message, the usage and an error status.
 */
def exitWithError(cli, msg) {
    println msg
    println ''

    printHelp(cli)

    System.exit(1)
}

/**
 * Backups the data.
 */
def backupData(binFolder) {
    def backup = System.console().readLine '> Backup the data folder before upgrade? [(Y)es/(N)o]: '
        backup = BooleanUtils.toBoolean(backup)

    if (backup) {
        println "========================================================================"
        println "Backing up data"
        println "========================================================================"

        def setupCallback = { pb ->
            def env = pb.environment()
                env.remove("CRAFTER_HOME")
                env.remove("DEPLOYER_HOME")
                env.remove("CRAFTER_BIN_DIR")
                env.remove("CRAFTER_DATA_DIR")
                env.remove("CRAFTER_LOGS_DIR")
        }

        executeCommand(["./crafter.sh", "backup"], binFolder, setupCallback)
    }
}

/**
 * Upgrade DB
 */
def upgradeDB(binFolder) {
    def upgrade = System.console().readLine '> Upgrade database? [(Y)es/(N)o]: '
        upgrade = BooleanUtils.toBoolean(upgrade)

    if (upgrade) {
        println "========================================================================"
        println "Upgrade database"
        println "========================================================================"

        def setupCallback = { pb ->
            def env = pb.environment()
                env.remove("CRAFTER_HOME")
                env.remove("DEPLOYER_HOME")
                env.remove("CRAFTER_BIN_DIR")
                env.remove("CRAFTER_DATA_DIR")
                env.remove("CRAFTER_LOGS_DIR")
                env.remove("MARIADB_HOME")
                env.remove("MARIADB_DATA_DIR")
        }

        executeCommand(["./crafter.sh", "upgradedb"], binFolder, setupCallback)
    }
}

/**
 * Backups the bin folder.
 */
def backupBin(binFolder, backupsFolder, environmentName) {
    def backup = System.console().readLine '> Backup the bin folder before upgrade? [(Y)es/(N)o]: '
        backup = BooleanUtils.toBoolean(backup)

    if (backup) {
        println "========================================================================"
        println "Backing up bin"
        println "========================================================================"

        def now = new Date()
        def backupTimestamp = now.format(backupTimestampFormat)

        if (!Files.exists(backupsFolder)) {
            Files.createDirectories(backupsFolder)
        }

        def backupBinFolder = backupsFolder.resolve("crafter-${environmentName}-bin.${backupTimestamp}.bak")

        println "Backing up bin folder to ${backupBinFolder}"

        NioUtils.copyDirectory(binFolder, backupBinFolder)
    }
}

/**
 * Shutdowns Crafter.
 */
def shutdownCrafter(binFolder) {
    println "========================================================================"
    println "Shutting down Crafter"
    println "========================================================================"

    def setupCallback = { pb ->
        def env = pb.environment()
            env.remove("CRAFTER_HOME")
            env.remove("DEPLOYER_HOME")
            env.remove("CRAFTER_BIN_DIR")
            env.remove("CRAFTER_DATA_DIR")
            env.remove("CRAFTER_LOGS_DIR")
    }

    executeCommand(["./shutdown.sh"], binFolder, setupCallback)
}

/**
 * Checks if the path belongs to what Crafter considers a config file.
 */
def isConfigFile(path) {
    return configFilePatterns.any { path.toString().matches(it) }
}

/**
 * Returns true if the checksum of both specified fiels is the same, false otherwise.
 */
def compareFiles(file1, file2) {
    def file1Md5
    def file2Md5

    Files.newInputStream(file1).withCloseable { inputStream ->
        file1Md5 = DigestUtils.md5Hex(inputStream)
    }
    Files.newInputStream(file2).withCloseable { inputStream ->
        file2Md5 = DigestUtils.md5Hex(inputStream)
    }

    return file1Md5 == file2Md5
}

/**
 * Executes the a diff between the specified files.
 */
def diffFiles(oldFile, newFile) {
    executeCommand(["/bin/sh", "-c", "diff ${oldFile} ${newFile} | less".toString()])
}

/**
 * Opens the default editor, pointed by $EDITOR. If the env variable doesn't exist, nano is used instead.
 */
def openEditor(path) {
    def command = System.getenv('EDITOR')
    if (!command) {
        command = 'nano'
    }

    executeCommand([command, "${path}".toString()])
}

/**
 * Overwrites the old file with the new file, backing up the old file first to ${OLD_FILE_PATH}.${TIMESTAMP}.bak.
 */
def overwriteFile(binFolder, oldFile, newFile, filePath) {
    def now = new Date()
    def backupTimestamp = now.format(backupTimestampFormat)
    def backupFilePath = "${filePath}.${backupTimestamp}.bak"
    def backupFile = binFolder.resolve(backupFilePath)

    println "[o] Overwriting config file ${filePath} with the new release version (backup of the old one will be at ${backupFilePath})"

    Files.move(oldFile, backupFile)
    Files.copy(newFile, oldFile, REPLACE_EXISTING)
}

/**
 * Prints the border of a menu, using the specified length
 */
def printMenuBorder(length) {
    for (i = 0; i < length; i++) {
        print '-'
    }

    println ''
}

/**
 * Prints the interactive "menu" that asks for the user input when the new version of a file differs from the old
 * version. 
 */
def showSyncFileMenu(filePath) {
    def firstLine = "Config file ${filePath} is different in the new release. Please choose:".toString()

    printMenuBorder(firstLine.length())
    println firstLine
    println ' - (D)iff files to see what changed'
    println ' - (E)dit the old file (with $EDITOR)'
    println " - (O)verwrite ${filePath} with the new version"
    println ' - (K)eep the old file and continue with the rest of the upgrade'
    println " - (A)lways overwrite files and don't ask again for the rest of the upgrade"
    println ' - (Q)uit the upgrade script (this will stop the upgrade at this point)'
    printMenuBorder(firstLine.length()) 

    def option = System.console().readLine '> Enter your choice: '
        option = StringUtils.lowerCase(option)

    return option
}

/**
 * Syncs an old file with it's new version.
 */
def syncFile(binFolder, newBinFolder, filePath, alwaysOverwrite) {
    def oldFile = binFolder.resolve(filePath)
    def newFile = newBinFolder.resolve(filePath)

    if (Files.isDirectory(newFile)) {
        if (!Files.exists(oldFile)) {
            println "[+] Creating new folder ${filePath}"

            Files.createDirectories(oldFile)
        }
    } else if (Files.exists(oldFile)) {
        if (isConfigFile(filePath)) {
            def options = ['d', 'e', 'o', 'k', 'a', 'q']
            def done = false

            while (!done) {
                if (compareFiles(oldFile, newFile)) {
                    done = true
                } else if (alwaysOverwrite) {
                    overwriteFile(binFolder, oldFile, newFile, filePath)
                    done = true
                } else {
                    def selectedOption = showSyncFileMenu(filePath)
                    switch (selectedOption) {
                        case 'd':
                            diffFiles(oldFile, newFile)
                            break
                        case 'e':
                            openEditor(oldFile)
                            break
                        case 'o':
                            overwriteFile(binFolder, oldFile, newFile, filePath)
                            done = true
                            break
                        case 'k':
                            done = true
                            break
                        case 'a':
                            overwriteFile(binFolder, oldFile, newFile, filePath)
                            done = true
                            alwaysOverwrite = true
                            break
                        case 'q':
                            println 'Quitting upgrade...'
                            System.exit(0)
                        default:
                            println "[!] Unrecognized option '${selectedOption}'"
                            break                       
                    }                       
                }
            }
        } else if (!compareFiles(oldFile, newFile)) {
            println "[o] Overwriting file ${filePath} with the new release version"

            Files.copy(newFile, oldFile, REPLACE_EXISTING)
        }
    } else {
        println "[+] Copying new file ${filePath}"

        def parent = oldFile.parent
        if (!Files.exists(parent)) {
            Files.createDirectories(parent)
        }

        Files.copy(newFile, oldFile)
    }

    return alwaysOverwrite
}

/**
 * Prints the interactive "menu" that asks for the user input when an old file doesn't appear in the new release.
 */
def showDeleteFileMenu(filePath) {
    def firstLine = "Config file ${filePath} doesn't exist in the new release. Delete the file?".toString()

    printMenuBorder(firstLine.length())
    println firstLine
    println ' - (N)o'
    println ' - (Y)es'
    println " - (A)lways delete the file and and don't ask again for the rest of the upgrade"
    println ' - (Q)uit the upgrade script (this will stop the upgrade at this point)'
    printMenuBorder(firstLine.length()) 

    def option = System.console().readLine '> Enter your choice: '
        option = StringUtils.lowerCase(option)

    return option    
}

/**
 * Checks if an old file needs to be deleted if it doesn't appear in the new release. 
 */
def deleteFileIfAbsentInNewRelease(binFolder, newBinFolder, filePath, alwaysDelete) {
    def oldFile = binFolder.resolve(filePath)
    def newFile = newBinFolder.resolve(filePath)
    def delete = false

    if (!Files.exists(newFile)) {
        if (!alwaysDelete && !Files.isDirectory(oldFile)) {
            def options = ['n', 'y', 'a', 'q']
            def done = false

            while (!done) {
                def selectedOption = showDeleteFileMenu(filePath)
                switch (selectedOption) {
                    case 'n':
                        done = true
                        break
                    case 'y':
                        delete = true
                        done = true
                        break
                    case 'a':
                        delete = true
                        alwaysDelete = true
                        done = true
                        break
                    case 'q':
                        println 'Quitting upgrade...'
                        System.exit(0)
                    default:
                        println "[!] Unrecognized option '${selectedOption}'"
                        break                            
                }                
            }
        } else {
            delete = true
        }
    }

    if (delete) {
        println "[-] Deleting file ${filePath} that doesn't exist in the new release"

        Files.delete(oldFile)
    }

    return alwaysDelete
}

/**
 * Clears Tomcat's temp folders and exploded webapps.
 */
def resetTomcat(binFolder) {
    def tempFolder = binFolder.resolve("apache-tomcat/temp")
    def workFolder = binFolder.resolve("apache-tomcat/work")
    def logsFolder = binFolder.resolve("apache-tomcat/logs")
    def webAppsFolder = binFolder.resolve("apache-tomcat/webapps")

    if (Files.exists(tempFolder)) {
        FileUtils.cleanDirectory(tempFolder.toFile())
    }
    if (Files.exists(workFolder)) {
        FileUtils.cleanDirectory(workFolder.toFile())
    }
    if (Files.exists(logsFolder)) {
        FileUtils.cleanDirectory(logsFolder.toFile())
    }
    if (Files.exists(webAppsFolder)) {
        Files.walk(webAppsFolder).withCloseable { files ->
            files
                .filter { file -> return file != webAppsFolder && Files.isDirectory(file) }
                .each { file -> FileUtils.deleteDirectory(file.toFile()) }
        }
    }
}

/**
 * Does the actual upgrade
 */
def doUpgrade(binFolder, newBinFolder) {
    println "========================================================================"
    println "Upgrading Crafter"
    println "========================================================================"

    resetTomcat(binFolder)
    resetTomcat(newBinFolder)

    println "Synching files from ${newBinFolder} to ${binFolder}..."

    def alwaysOverwrite = false
    def alwaysDelete = false

    // Delete files in the old bundle that are absent in the new bundle
    Files.walk(binFolder).withCloseable { files ->
        files
            .sorted(Comparator.reverseOrder())
            .each { file ->
                alwaysDelete = deleteFileIfAbsentInNewRelease(
                    binFolder, newBinFolder, binFolder.relativize(file), alwaysDelete)
            }
    }    

    // Sync the files between the old bundle and the new bundle
    Files.walk(newBinFolder).withCloseable { files ->
        files
            .sorted(Comparator.reverseOrder())
            .each { file ->
                alwaysOverwrite = syncFile(binFolder, newBinFolder, newBinFolder.relativize(file), alwaysOverwrite)
            }
    }

    upgradeDB(binFolder)
}

/**
 * Executes the upgrade.
 */
def upgrade(targetFolder) {
    def binFolder = targetFolder.resolve("bin")
    def backupsFolder = targetFolder.resolve("backups")
    def newBinFolder = getCrafterBinFolder()

    shutdownCrafter(binFolder)
    backupData(binFolder)
    backupBin(binFolder, backupsFolder, getEnvironmentName())
    doUpgrade(binFolder, newBinFolder)

    println "========================================================================"
    println "Upgrade complete"
    println "========================================================================"
    println "Please read the release notes before starting Crafter again for any additional changes you need to " +
            "manually apply"
}

checkDownloadGrapesOnlyMode(getClass())

def cli = new CliBuilder(usage: 'upgrade-target [options] <target-installation-path>')
buildCli(cli)

def options = cli.parse(args)
if (options) {
    // Show usage text when -h or --help option is used.
    if (options.help) {
        printHelp(cli)
        return
    }    

    // Parse the options and arguments
    def extraArguments = options.arguments()
    if (CollectionUtils.isNotEmpty(extraArguments)) {
        def targetPath = extraArguments[0]
        def targetFolder = Paths.get(targetPath)

        upgrade(targetFolder)
    } else {
        exitWithError(cli, 'No <target-installation-path> was specified')
    }
}
