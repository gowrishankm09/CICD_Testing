pipeline {
    agent {
        label 'BGL700750'  // Connects to Windows Agent
    }
    // Stage to dynamically assign git variables to env
    stages {
        stage('Preprocess Git Variables') {
            steps {
                script {
                    // Fallback if env.GIT_URL is not automatically available
                    def gitUrl = env.GIT_URL ?: bat(script: "\"C:\\Program Files\\Git\\bin\\git.exe\" config --get remote.origin.url", returnStdout: true).trim()
                    echo "Original Git URL : ${gitUrl}"
                    // Replace domain part
                    def modifiedGitUrl = gitUrl.replace('git@t-github.t.rd.honda.com', 'git@173.23.5.110')
                    echo "Modified Git URL : ${modifiedGitUrl}"
                    // Extract repo name
                    def matcher = gitUrl =~ /.*\/(.*?)\.git/
                    def repoName = matcher ? matcher[0][1] : 'UnknownRepo'
                    echo "Repository Name  : ${repoName}"
                    // Export to env for later use
                    env.MODIFIED_GIT_URL = gitUrl
                    env.REPO_NAME = repoName
                }
            }
        }
        stage('Continue Pipeline') {
            environment {
                PATH         = "${env.PATH};C:\\Windows\\System32;C:\\Program Files\\MATLAB\\R2017b\\bin"
                GITLAB_URL   = "${env.MODIFIED_GIT_URL}"
                REPO_NAME    = "${env.REPO_NAME}"
                Branch       = "${env.gitlabBranch}"
                Build_Id     = "${env.BUILD_ID}"
                REPO_DIR     = "C:\\jenkins\\workspace\\IDS_Unit_Model_Pipeline\\${Build_Id}_JobId"
                REPO_DIR_MOD = "C:\\jenkins\\workspace\\IDS_Unit_Model_Pipeline\\${Build_Id}_JobId\\${REPO_NAME}"
                wkr_dir      = "C:\\jenkins\\workspace\\IDS_Unit_Model_Pipeline"
                delete_dir   = "C:\\jenkins\\workspace\\IDS_Unit_Model_Pipeline\\${Build_Id}_JobId"
                GITLAB_COMMIT_ID = "${env.gitlabAfter}"
                CONSOLE_URL  = "${BUILD_URL}console"
                WORKSPACE_URL = "${BUILD_URL}ws/"
                OUTPUT_DIR   = "C:\\jenkins\\workspace\\IDS_Unit_Model_Pipeline\\${Build_Id}_JobId\\output"
                PathArgument = "C:\\jenkins\\workspace\\IDS_Unit_Model_Pipeline\\${Build_Id}_JobId"
                buildFailed  = false
            }
            stages {
                stage('STAGE 1/7 : Pipeline is Triggered') {
                    steps {
                        script {
                            buildFailed = false
                        }
                    }
                }
                stage('STAGE 2/7 : Cloning and Fetching Modified File Path') {
                    steps {
                        script {
                                echo "The Reponame is ${env.REPO_NAME}"
    // Clone the repository
    echo "Cloning repository into ${env.REPO_DIR}..."
    dir(env.REPO_DIR) {
        bat(returnStdout: true, script: "\"C:\\Program Files\\Git\\bin\\bash.exe\" -c 'git clone --no-checkout ${env.GITLAB_URL}'")
    }
    // Create output directory and log file
    bat "mkdir ${env.OUTPUT_DIR}"
    bat "type nul > ${env.OUTPUT_DIR}\\${env.BUILD_ID}_consolelog.txt"
    // Fetch modified file paths
    dir(env.REPO_DIR_MOD) {
        def gitStatusOutput = bat(returnStdout: true, script: "\"C:\\Program Files\\Git\\bin\\bash.exe\" -c 'git diff-tree --no-commit-id --name-only -r ${env.GITLAB_COMMIT_ID}'").trim()
        def uniquePaths = extractUniquePaths(gitStatusOutput)
        echo "Unit models (unique): ${uniquePaths}"
        env.unitmodelpaths = "${uniquePaths}"
        env.clonedModels = uniquePaths.join(',')
        echo "The cloned models are ${env.clonedModels}"
        sparseCheckoutAndValidateFiles(uniquePaths, gitStatusOutput)
    }
                        }
                    }
                }
                stage('STAGE 3/7 : EDJ Model Compilation and Hayabusa Check Guidelines') {
                    when {
                        expression { !buildFailed }
                    }
                    steps {
                        script {
                            compileAndRunHayabusaChecks()
                        }
                    }
                }
            }
        }
    }
}
// ===== Helper Methods Below =====
def cloneAndFetchModifiedFiles() {
    echo "The Reponame is ${env.REPO_NAME}"
    // Clone the repository
    echo "Cloning repository into ${env.REPO_DIR}..."
    dir(env.REPO_DIR) {
        bat(returnStdout: true, script: "\"C:\\Program Files\\Git\\bin\\bash.exe\" -c 'git clone --no-checkout ${env.GITLAB_URL}'")
    }
    // Create output directory and log file
    bat "mkdir ${env.OUTPUT_DIR}"
    bat "type nul > ${env.OUTPUT_DIR}\\${env.BUILD_ID}_consolelog.txt"
    // Fetch modified file paths
    dir(env.REPO_DIR_MOD) {
        def gitStatusOutput = bat(returnStdout: true, script: "\"C:\\Program Files\\Git\\bin\\bash.exe\" -c 'git diff-tree --no-commit-id --name-only -r ${env.GITLAB_COMMIT_ID}'").trim()
        def uniquePaths = extractUniquePaths(gitStatusOutput)
        echo "Unit models (unique): ${uniquePaths}"
        env.unitmodelpaths = "${uniquePaths}"
        env.clonedModels = uniquePaths.join(',')
        echo "The cloned models are ${env.clonedModels}"
        sparseCheckoutAndValidateFiles(uniquePaths, gitStatusOutput)
    }
}
