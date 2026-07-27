pipeline {
    agent any

    environment {
        DOCKER_IMAGE   = 'beliver247/green-test-backend'
        DOCKER_TAG     = "${BUILD_NUMBER}"
        DASHBOARD_URL  = 'http://host.docker.internal:5003' // Set to http://localhost:5003 if Jenkins runs natively on host
    }

    stages {
        stage('Notify Start') {
            steps {
                script {
                    env.PIPELINE_START = System.currentTimeMillis().toString()
                    env.COMMIT_SHA = ''
                    env.COMMIT_MSG = ''
                    env.WORK_DIR = fileExists('pom.xml') ? '.' : 'green-release-demo'
                }
            }
        }

        stage('Setup Tools') {
            steps {
                script {
                    def workspace = pwd()
                    echo "Setting up local tool binaries (Docker CLI and Maven)..."
                    
                    sh 'mkdir -p tool-bin'
                    
                    // Install Docker CLI if not available
                    if (sh(script: 'command -v docker >/dev/null 2>&1', returnStatus: true) != 0) {
                        if (!fileExists('tool-bin/docker')) {
                            echo "Docker CLI not found. Downloading static binary..."
                            sh '''
                                curl -fsSL https://download.docker.com/linux/static/stable/x86_64/docker-27.3.1.tgz -o docker.tgz
                                tar -xzf docker.tgz --strip-components=1 -C tool-bin docker/docker
                                rm -f docker.tgz
                                chmod +x tool-bin/docker
                            '''
                        }
                    } else {
                        echo "System docker command is already available."
                    }
                    
                    // Install Docker Compose if not available
                    if (sh(script: 'command -v docker-compose >/dev/null 2>&1', returnStatus: true) != 0) {
                        if (!fileExists('tool-bin/docker-compose')) {
                            echo "Docker Compose not found. Downloading static binary..."
                            sh '''
                                curl -fsSL https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 -o tool-bin/docker-compose
                                chmod +x tool-bin/docker-compose
                            '''
                        }
                    } else {
                        echo "System docker-compose command is already available."
                    }
                    
                    // Install Maven if not available
                    if (sh(script: 'command -v mvn >/dev/null 2>&1', returnStatus: true) != 0) {
                        if (!fileExists('tool-bin/maven/bin/mvn')) {
                            echo "Maven not found. Downloading Apache Maven..."
                            sh '''
                                curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz -o maven.tar.gz
                                mkdir -p tool-bin/maven
                                tar -xzf maven.tar.gz -C tool-bin/maven --strip-components=1
                                rm -f maven.tar.gz
                                chmod +x tool-bin/maven/bin/mvn
                            '''
                        }
                    } else {
                        echo "System mvn command is already available."
                    }
                    
                    // Update PATH environment variable globally for the workspace
                    env.PATH = "${workspace}/tool-bin:${workspace}/tool-bin/maven/bin:${env.PATH}"
                    
                    sh 'docker --version || echo "Docker CLI installed but check failed"'
                    sh 'mvn --version || echo "Maven installed but check failed"'
                }
            }
        }

        stage('Checkout') {
            steps {
                script {
                    // Check if we are running in the context of the main repo or need to clone
                    if (!fileExists('pom.xml')) {
                        echo "Checking out green-release-demo..."
                        dir(env.WORK_DIR) {
                            checkout([$class: 'GitSCM',
                                branches: [[name: '*/main']],
                                userRemoteConfigs: [[
                                    url: 'https://github.com/Beliver-247/green-release-demo.git'
                                ]],
                                extensions: [[ $class: 'CloneOption', shallow: false, depth: 0, noTags: false ]]
                            ])
                        }
                    } else {
                        echo "Found pom.xml in workspace root. Using existing root checkout."
                        sh "git status || echo 'Not a git repo'"
                        sh "git rev-parse HEAD || echo 'No git commit'"
                        echo "Forcing checkout of latest main..."
                        sh """
                            git fetch origin main || true
                            git checkout -B main origin/main || true
                            git pull origin main || true
                        """
                    }
                    
                    dir(env.WORK_DIR) {
                        env.COMMIT_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                        env.COMMIT_MSG = sh(script: 'git log -1 --pretty=%s', returnStdout: true).trim()
                    }
                }
            }
        }

        stage('Build') {
            steps {
                dir(env.WORK_DIR) {
                    script {
                        def buildStart = System.currentTimeMillis()
                        echo "Running full Maven build..."
                        sh 'mvn clean install -DskipTests'
                        env.BUILD_DURATION = ((System.currentTimeMillis() - buildStart) / 1000.0).toString()
                    }
                }
            }
        }

        stage('Test') {
            steps {
                dir(env.WORK_DIR) {
                    script {
                        def testStart = System.currentTimeMillis()
                        echo "Running all Maven tests..."
                        def testOutput = sh(script: 'mvn test', returnStdout: true)
                        env.TEST_DURATION = ((System.currentTimeMillis() - testStart) / 1000.0).toString()

                        // Parse test counts from Maven surefire output
                        def testsRun = 0
                        def moduleDetails = [
                            'core': ['status': 'run', 'run': 6, 'skipped': 0],
                            'service': ['status': 'run', 'run': 0, 'skipped': 0],
                            'api': ['status': 'run', 'run': 7, 'skipped': 0],
                            'app': ['status': 'run', 'run': 0, 'skipped': 0]
                        ]
                        
                        def matcher = (testOutput =~ /Tests run: (\d+),/)
                        while (matcher.find()) {
                            testsRun += Integer.parseInt(matcher.group(1))
                        }
                        env.TESTS_EXECUTED = testsRun.toString()
                        env.MODULE_DETAILS = groovy.json.JsonOutput.toJson(moduleDetails).replaceAll('"', '\\\\"')
                        
                        echo "Total tests executed: ${env.TESTS_EXECUTED}"
                        echo "Module Details: ${env.MODULE_DETAILS}"
                    }
                }
            }
        }

        stage('Docker Build') {
            steps {
                dir(env.WORK_DIR) {
                    script {
                        def dockerStart = System.currentTimeMillis()
                        dir('api') {
                            echo "Building Docker image: ${DOCKER_IMAGE}:${DOCKER_TAG}"
                            sh "docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} -t ${DOCKER_IMAGE}:latest ."
                        }
                        env.DOCKER_BUILD_DURATION = ((System.currentTimeMillis() - dockerStart) / 1000.0).toString()
                    }
                }
            }
        }

        stage('Deploy Locally') {
            steps {
                dir(env.WORK_DIR) {
                    script {
                        def deployStart = System.currentTimeMillis()
                        echo "Deploying app locally on port 8087..."
                        sh """
                            export HOST_PORT=8087
                            docker-compose -p green-opt down || true
                            # Kill any stale container holding port 8087
                            docker ps -q --filter "publish=8087" | xargs docker rm -f || true
                            docker-compose -p green-opt up -d
                            sleep 15
                            docker-compose -p green-opt ps
                        """
                        env.DEPLOY_DURATION = ((System.currentTimeMillis() - deployStart) / 1000.0).toString()
                    }
                }
            }
        }

        stage('Smoke Test (Local)') {
            steps {
                dir(env.WORK_DIR) {
                    sh '''
                        echo "Checking health endpoint..."
                        for i in 1 2 3 4 5 6 7 8 9 10; do
                            echo "Attempt $i..."
                            if curl -s -f http://localhost:8087/health; then
                                echo "\\nSMOKE_TEST_PASSED"
                                exit 0
                            fi
                            if curl -s -f http://host.docker.internal:8087/health; then
                                echo "\\nSMOKE_TEST_PASSED"
                                exit 0
                            fi
                            if curl -s -f http://172.17.0.1:8087/health; then
                                echo "\\nSMOKE_TEST_PASSED"
                                exit 0
                            fi
                            sleep 5
                        done
                        echo "Smoke test failed!"
                        exit 1
                    '''
                }
            }
        }
    }

    post {
        always {
            dir(env.WORK_DIR) {
                script {
                    def totalDuration = (System.currentTimeMillis() - env.PIPELINE_START.toLong()) / 1000.0

                    // Send metrics to GreenDevOps Dashboard
                    def cleanCommitMsg = (env.COMMIT_MSG ?: '').replaceAll('"', '\\\\"')
                    def jsonPayload = """{
                        "job_name": "${env.JOB_NAME}",
                        "build_number": "${env.BUILD_NUMBER}",
                        "pipeline_type": "unoptimized",
                        "commit_sha": "${env.COMMIT_SHA ?: ''}",
                        "commit_message": "${cleanCommitMsg}",
                        "status": "${currentBuild.currentResult ?: 'UNKNOWN'}",
                        "total_duration_s": ${totalDuration},
                        "build_duration_s": ${env.BUILD_DURATION ?: 'null'},
                        "test_duration_s": ${env.TEST_DURATION ?: 'null'},
                        "docker_duration_s": ${env.DOCKER_BUILD_DURATION ?: 'null'},
                        "deploy_duration_s": ${env.DEPLOY_DURATION ?: 'null'},
                        "optimizer_duration_s": null,
                        "modules_built": "all",
                        "modules_tested": "all",
                        "tests_executed": ${env.TESTS_EXECUTED ?: 0},
                        "tests_skipped": 0,
                        "module_details": "${env.MODULE_DETAILS ?: ''}",
                        "build_command": "mvn clean install -DskipTests",
                        "test_command": "mvn test"
                    }"""

                    writeFile file: 'dashboard_payload.json', text: jsonPayload
                    
                    // Post to dashboard - try different host endpoints
                    sh """
                        curl -s -X POST ${DASHBOARD_URL}/api/builds \
                            -H "Content-Type: application/json" \
                            -d @dashboard_payload.json || \
                        curl -s -X POST http://localhost:5003/api/builds \
                            -H "Content-Type: application/json" \
                            -d @dashboard_payload.json || \
                        curl -s -X POST http://127.0.0.1:5003/api/builds \
                            -H "Content-Type: application/json" \
                            -d @dashboard_payload.json || \
                        curl -s -X POST http://172.17.0.1:5003/api/builds \
                            -H "Content-Type: application/json" \
                            -d @dashboard_payload.json || \
                        echo "Failed to send metrics to dashboard."
                    """
                }

                // Cleanup build image
                sh "docker rmi ${DOCKER_IMAGE}:${DOCKER_TAG} || true"
                sh "docker image prune -f || true"
            }
        }
        success {
            echo "Deployment SUCCESSFUL — Build #${BUILD_NUMBER}"
        }
        failure {
            echo "Deployment FAILED — Build #${BUILD_NUMBER}"
        }
    }
}
