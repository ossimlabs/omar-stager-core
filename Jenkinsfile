properties([
     parameters([
        string(name: 'PROJECT_URL', defaultValue: 'https://github.com/<organization/service-name>', description: 'The project github URL'),
        string(name: 'DOCKER_REGISTRY_DOWNLOAD_URL', defaultValue: 'nexus-docker-private-group.ossim.io', description: 'Repository of docker images')
    ]),
    pipelineTriggers([[$class: "GitHubPushTrigger"]]),
    [$class: 'GithubProjectProperty', displayName: '', projectUrlStr: 'https://github.com/ossimlabs/omar-stager-core'],
    buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '3', daysToKeepStr: '', numToKeepStr: '20'))
])

podTemplate(containers: [
    containerTemplate(
        name: 'jdk11',
        image: 'openjdk:11',
        ttyEnabled: true,
        command: 'cat'
    )]
) 
{
  node(POD_LABEL) {
     stage("Checkout branch") {
        APP_NAME = PROJECT_URL.tokenize('/').last()
        scmVars = checkout(scm)
        Date date = new Date()
        String currentDate = date.format("YYYY-MM-dd-HH-mm-ss")
        MASTER = "master"
        GIT_BRANCH_NAME = scmVars.GIT_BRANCH
        BRANCH_NAME = """${sh(returnStdout: true, script: "echo ${GIT_BRANCH_NAME} | awk -F'/' '{print \$2}'").trim()}"""
        VERSION = """${sh(returnStdout: true, script: "cat chart/Chart.yaml | grep version: | awk -F'version:' '{print \$2}'").trim()}"""
        GIT_TAG_NAME = APP_NAME + "-" + VERSION
        ARTIFACT_NAME = "ArtifactName"

            if (BRANCH_NAME == "${MASTER}") {
                buildName "${VERSION}"
                TAG_NAME = "${VERSION}"
            }
            else {
                buildName "${BRANCH_NAME}-${currentDate}"
                TAG_NAME = "${BRANCH_NAME}-${currentDate}"
            }
        
    }

    stage("Load Variables") {
        withCredentials([string(credentialsId: 'o2-artifact-project', variable: 'o2ArtifactProject')]) {
            step ([$class: "CopyArtifact",
                projectName: o2ArtifactProject,
                filter: "common-variables.groovy",
                flatten: true])
        }
        load "common-variables.groovy"
        DOCKER_IMAGE_PATH = "${DOCKER_REGISTRY_PRIVATE_UPLOAD_URL}/${APP_NAME}"
    }
      
      
      
  stage('Build') {
    container('builder') {
        sh """
            ./gradlew assemble -PossimMavenProxy=${MAVEN_DOWNLOAD_URL}
        """
    archiveArtifacts "plugins/*/build/libs/*.jar"
    }
}
      
  

      stage("Publish Nexus") {
        withCredentials([[$class          : 'UsernamePasswordMultiBinding',
                          credentialsId   : 'nexusCredentials',
                          usernameVariable: 'MAVEN_REPO_USERNAME',
                          passwordVariable: 'MAVEN_REPO_PASSWORD']]) {
          sh """
            ./gradlew publish -PossimMavenProxy=${MAVEN_DOWNLOAD_URL}
          """
        }
      }
    }
  }
