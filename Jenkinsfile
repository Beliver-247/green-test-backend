pipeline {
    agent any

    environment {
        DOCKER_IMAGE = 'beliver247/green-test-backend'
        DOCKER_TAG   = "${BUILD_NUMBER}"
        REPO_URL     = 'https://github.com/Beliver-247/green-test-backend.git'
        HOST_PORT    = '8087'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: "${REPO_URL}"
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean install -DskipTests'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn test'
            }
        }

        stage('Docker Build') {
            steps {
                dir('api') {
                    sh "docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} -t ${DOCKER_IMAGE}:latest ."
                }
            }
        }

        stage('Deploy Locally') {
            steps {
                sh """
                    docker-compose -p green-std down || true
                    docker ps -q --filter "publish=${HOST_PORT}" | xargs -r docker rm -f
                    docker-compose -p green-std up -d
                    sleep 15
                    docker-compose -p green-std ps
                """
            }
        }

        stage('Smoke Test') {
            steps {
                sh """
                    for i in \$(seq 1 10); do
                        if curl -sf http://localhost:${HOST_PORT}/health; then
                            echo "\nSmoke test passed"
                            exit 0
                        fi
                        sleep 5
                    done
                    echo "Smoke test failed"
                    exit 1
                """
            }
        }
    }

    post {
        always {
            sh "docker rmi ${DOCKER_IMAGE}:${DOCKER_TAG} || true"
            sh 'docker image prune -f || true'
        }
        success {
            echo "Deployment SUCCESSFUL — Build #${BUILD_NUMBER}"
        }
        failure {
            echo "Deployment FAILED — Build #${BUILD_NUMBER}"
        }
    }
}