pipeline {
    agent any

    parameters {
        booleanParam(
            name: 'DRY_RUN',
            defaultValue: false,
            description: 'When true, only run the optimizer analysis without building, testing, or deploying.'
        )
        booleanParam(
            name: 'FORCE_FULL_BUILD',
            defaultValue: false,
            description: 'Skip optimization and force a full build and test.'
        )
        booleanParam(
            name: 'ENABLE_GREEN_SCHEDULING',
            defaultValue: true,
            description: 'Allow the pipeline to delay the build until a greener time window.'
        )
        string(
            name: 'OVERRIDE_SCHEDULE_HOUR',
            defaultValue: 'auto',
            description: 'Override ML recommendation (e.g., "5" for 5 AM). Use "auto" to let the ML model decide.'
        )
    }

    environment {
        DOCKER_IMAGE = 'beliver247/green-test-backend'
        DOCKER_TAG   = "${BUILD_NUMBER}"
        REPO_URL     = 'https://github.com/Beliver-247/green-test-backend.git'
        HOST_PORT    = '8087'
        ELECTRICITY_MAPS_API_KEY = 'em_nGgVAPUefFX2qe8BkqzFgw3n8uGpJE2J'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: "${REPO_URL}"
            }
        }

        stage('Green Optimizer') {
            steps {
                script {
                    if (params.FORCE_FULL_BUILD) {
                        env.OPTIMIZER_STATUS = 'success'
                        env.BUILD_CMDS = 'mvn clean install -DskipTests'
                        env.TEST_CMDS  = 'mvn test'
                    } else {
                        def out = sh(script: "tar -cf - . | docker run --rm -i -e ELECTRICITY_MAPS_API_KEY=\"\${ELECTRICITY_MAPS_API_KEY}\" beliver247/build-optimizer-agent:latest bash -lc 'mkdir -p /w && tar -xf - -C /w && cd /w && git config --global --add safe.directory /w && export GIT_PREVIOUS_SUCCESSFUL_COMMIT=\$(cat .last_built_commit 2>/dev/null || git rev-parse HEAD~1 2>/dev/null || echo HEAD) GIT_COMMIT=\$(git rev-parse HEAD) && python3 -m optimizer --project-root . --dry-run true --output-format json --carbon-aware || true'", returnStdout: true)
                        def j = new groovy.json.JsonSlurper().parseText(out.substring(out.indexOf('{')))
                        env.OPTIMIZER_STATUS = j.status
                        env.BUILD_CMDS = j.actions?.findAll{it.name=='build'}?.collect{it.command.join(' ')}?.join(' && ') ?: ''
                        env.TEST_CMDS  = j.actions?.findAll{it.name=='test'}?.collect{it.command.join(' ')}?.join(' && ') ?: ''
                        env.SCHEDULING_ACTION = j.scheduling?.action ?: 'execute_now'
                        env.SCHEDULED_HOUR = j.scheduling?.scheduled_hour?.toString() ?: '0'
                    }
                }
            }
        }

        stage('Green Scheduling') {
            when { expression { params.ENABLE_GREEN_SCHEDULING && env.OPTIMIZER_STATUS == 'success' } }
            steps {
                script {
                    def target = params.OVERRIDE_SCHEDULE_HOUR != 'auto' ? params.OVERRIDE_SCHEDULE_HOUR.toInteger() : env.SCHEDULED_HOUR.toInteger()
                    
                    if (env.SCHEDULING_ACTION == 'schedule' || params.OVERRIDE_SCHEDULE_HOUR != 'auto') {
                        def delaySecs = ((target - new Date().getHours() + 24) % 24) * 3600
                        echo "🌿 Carbon intensity is high. ML Model recommends delaying until ${target}:00."
                        echo "Queueing a new build to start in ${delaySecs} seconds..."
                        build job: env.JOB_NAME, quietPeriod: delaySecs, wait: false, parameters: [
                            booleanParam(name: 'ENABLE_GREEN_SCHEDULING', value: false),
                            booleanParam(name: 'FORCE_FULL_BUILD', value: params.FORCE_FULL_BUILD)
                        ]
                        error("Pipeline rescheduled to ${target}:00 to save carbon.")
                    } else {
                        echo "🌿 ML Model says it's a Green Window right now! Proceeding with build."
                    }
                }
            }
        }

        stage('Build') {
            when { expression { env.OPTIMIZER_STATUS == 'success' && env.BUILD_CMDS != '' } }
            steps {
                sh env.BUILD_CMDS
            }
        }

        stage('Test') {
            when { expression { env.OPTIMIZER_STATUS == 'success' && env.TEST_CMDS != '' } }
            steps {
                sh env.TEST_CMDS
            }
        }

        stage('Docker Build') {
            when { expression { env.OPTIMIZER_STATUS == 'success' } }
            steps {
                dir('api') {
                    sh "docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} -t ${DOCKER_IMAGE}:latest ."
                }
            }
        }

        stage('Deploy Locally') {
            when { expression { env.OPTIMIZER_STATUS == 'success' } }
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

      /*  stage('Smoke Test') {
            when { expression { env.OPTIMIZER_STATUS == 'success' } }
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
        } */
    }

    post {
        always {
            sh "docker rmi ${DOCKER_IMAGE}:${DOCKER_TAG} || true"
            sh 'docker image prune -f || true'
        }
        success {
            sh 'git rev-parse HEAD > .last_built_commit || true'
            echo "Deployment SUCCESSFUL — Build #${BUILD_NUMBER}"
        }
        failure {
            echo "Deployment FAILED — Build #${BUILD_NUMBER}"
        }
    }
}