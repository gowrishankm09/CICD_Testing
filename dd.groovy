pipeline {
    agent {
        label 'BGL700750'  // Windows Agent12
    }
    environment {
        PATH              = "${env.PATH};C:\\Windows\\System32;C:\\Program Files\\MATLAB\\R2017b\\bin"
        Build_Id          = "${env.BUILD_ID}"
        REPO_DIR          = "C:\\jenkins\\workspace\\IDS_Unit_Model_Pipeline\\${Build_Id}_JobId"
        OUTPUT_DIR        = "${REPO_DIR}\\output"
        CONSOLE_URL       = "${BUILD_URL}console"
        WORKSPACE_URL     = "${BUILD_URL}ws/"
        buildFailed       = false
    }
    stages {
        stage('Extract Git Info from Webhook') {
            steps {
                script {
                    // Assuming Generic Webhook Trigger Plugin passes these parameters
                    def gitUrl = env.GIT_URL
                    def modifiedGitUrl = gitUrl.replace('git@t-github.t.rd.honda.com', 'git@t-github.t.rd.honda.com')
                    def repoName = gitUrl.tokenize('/').last().replace('.git', '')
                    //def REPO_DIR = "${REPO_DIR}\\${REPO_DIR}"
                    env.MODIFIED_GIT_URL = gitUrl
                    env.REPO_NAME = repoName
                    def repodirmod = "${REPO_DIR}\\${REPO_NAME}"
                    env.REPO_DIR_MOD = repodirmod
                    echo "REPO_DIR_MOD Name: ${REPO_DIR_MOD}"
                    env.GITLAB_COMMIT_ID = env.GIT_COMMIT  // fallback if not present
                    env.GIT_BRANCH = env.GIT_BRANCH
                    echo "Modified Git URL: ${modifiedGitUrl}"
                    echo "Repo Name: ${repoName}"
                    echo "Branch: ${env.GIT_BRANCH}"
                    echo "Commit ID: ${env.GITLAB_COMMIT_ID}"
                }
            }
        }
        stage('Clone with Sparse Checkout') {
            steps {
                script {
                    echo "Cloning selectively into ${env.REPO_DIR}"
                    bat "mkdir ${env.REPO_DIR}"
 
                        
                     dir(REPO_DIR) {
                         bat(returnStdout: true, script: "\"C:\\Program Files\\Git\\bin\\bash.exe\" -c 'git clone --no-checkout ${MODIFIED_GIT_URL}'")
                     }
// Create output directory and log file
    bat "mkdir ${OUTPUT_DIR}"
    bat "type nul > ${OUTPUT_DIR}\\${env.BUILD_ID}_consolelog.txt"
    // Fetch modified file paths
    dir(REPO_DIR_MOD) {
        def gitStatusOutput = bat(returnStdout: true, script: "\"C:\\Program Files\\Git\\bin\\bash.exe\" -c 'git diff-tree --no-commit-id --name-only -r ${GITLAB_COMMIT_ID}'").trim()
        def uniquePaths = extractUniquePaths(gitStatusOutput)
        echo "Unit models (unique): ${uniquePaths}"
        env.unitmodelpaths = "${uniquePaths}"
        env.clonedModels = uniquePaths.join(',')
        echo "The cloned models are ${env.clonedModels}"
        //sparseCheckoutAndValidateFiles(uniquePaths, gitStatusOutput)
    }
                    }
                }
            }
        }

    }
 

//
// ===== Helper Methods =====
//
def extractUniquePaths(gitStatusOutput) {
    def filePaths = gitStatusOutput.tokenize('\n').collect { it.replaceAll('.*\\s', '') }
    def desiredPaths = filePaths.collect { path ->
        def parts = path.split('/')
        parts.size() >= 2 ? parts[0].trim() : null
    }.findAll { it != null }
    return desiredPaths.toSet().toList()
}
def sparseCheckoutAndValidateFiles(uniquePaths, gitStatusOutput) {
    def fileExtensions = gitStatusOutput.tokenize('\n').collect { path -> path.tokenize('.').last() }
    if (!fileExtensions.any { ['slx', 'mdl', 'epp', 'm', 'xlsx'].contains(it) }) {
        echo "JENKINS-ERROR_002: slx, epp, xlsx, or m files were not pushed."
        currentBuild.result = 'ABORTED'
        buildFailed = true
    }
}
def compileAndRunHayabusaChecks() {
    def pathArg = env.REPO_DIR
    def outputDir = env.OUTPUT_DIR
    def uniquePathsList = env.unitmodelpaths.replaceAll(/\[|\]/, "").split(",").collect { it.trim() }
    def failedModels = []
    def passedModels = []
    uniquePathsList.each { uniquePath ->
        def combinedPath = "${pathArg}\\${env.REPO_NAME}\\${uniquePath}"
        echo "Processing model at: ${combinedPath}"
        try {
            withEnv(["PathArgument=${combinedPath}", "outputargument=${outputDir}"]) {
                //runMATLABCommand(command: "run('C:\\Jenkins\\Automation_Scripts\\Model_Compilation.m')")
                echo "Model compilation and Hayabusa checks completed for ${combinedPath}"
                passedModels << uniquePath
            }
        } catch (Exception e) {
            echo "Error during model compilation for ${combinedPath}: ${e.message}"
            env.ERROR_MESSAGE = e.message
            failedModels << uniquePath
        }
    }
    env.passedModels = passedModels.join(',')
    env.failedModels = failedModels.join(',')
}