def isSASTEnabled
def isSASTPlusMEnabled
def isSCAEnabled
def isDASTEnabled
def isDASTPlusMEnabled

pipeline {
    agent any
    tools {
        nodejs 'NodeJS'
    }
    stages {
        stage('Checkout Source Code') {
            steps {
                git branch: 'devsecops', url: 'https://github.com/devsecops-test/vulnerable-node'
            }
        }

        stage('Build Source Code') {
            steps {
                sh 'npm install > npm-install.log'
            }
        }

        stage('IO - Prescription') {
            steps {
                synopsysIO(connectors: [
                    io(
                        configName: 'io-azure',
                        projectName: 'devsecops-vulnerable-node',
                        workflowVersion: '2021.12.2'),
                    github(
                        branch: 'devsecops',
                        configName: 'github-devsecops',
                        owner: 'devsecops-test',
                        repositoryName: 'vulnerable-node'),
                    buildBreaker(configName: 'BB-ALL')]) {
                        sh 'io --stage io'
                    }

                script {
                    def prescriptionJSON = readJSON file: 'io_state.json'

                    print("Business Criticality Score: $prescriptionJSON.Data.Prescription.RiskScore.BusinessCriticalityScore")
                    print("Data Class Score: $prescriptionJSON.Data.Prescription.RiskScore.DataClassScore")
                    print("Access Score: $prescriptionJSON.Data.Prescription.RiskScore.AccessScore")
                    print("Open Vulnerability Score: $prescriptionJSON.Data.Prescription.RiskScore.OpenVulnerabilityScore")
                    print("Change Significance Score: $prescriptionJSON.Data.Prescription.RiskScore.ChangeSignificanceScore")
                    print("Tooling Score: $prescriptionJSON.Data.Prescription.RiskScore.ToolingScore")
                    print("Training Score: $prescriptionJSON.Data.Prescription.RiskScore.TrainingScore")

                    isSASTEnabled = prescriptionJSON.Data.Prescription.Security.Activities.Sast.Enabled
                    isSASTPlusMEnabled = prescriptionJSON.Data.Prescription.Security.Activities.SastPlusM.Enabled
                    isSCAEnabled = prescriptionJSON.Data.Prescription.Security.Activities.Sca.Enabled
                    isDASTEnabled = prescriptionJSON.Data.Prescription.Security.Activities.Dast.Enabled
                    isDASTPlusMEnabled = prescriptionJSON.Data.Prescription.Security.Activities.DastPlusM.Enabled

                    print("SAST Enabled: $isSASTEnabled")
                    print("SAST+Manual Enabled: $isSASTPlusMEnabled")
                    print("SCA Enabled: $isSCAEnabled")
                    print("DAST Enabled: $isDASTEnabled")
                    print("DAST+Manual Enabled: $isDASTPlusMEnabled")
                }
            }
        }

        stage('SAST - ESLint') {
            when {
                expression { isSASTEnabled }
            }
            steps {
                echo 'Running SAST using ESLint'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/eslint/eslint-adapter.json --output eslint-adapter.json'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/eslint/eslint.sh --output eslint.sh'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/eslint/eslintrc.json --output .eslintrc.json'
                synopsysIO() {
                    sh 'io --stage execution --adapters eslint-adapter.json --state io_state.json || true'
                }
            }
        }

        stage('SAST Plus Manual') {
            when {
                expression { isSASTPlusMEnabled }
            }
            input {
                message "Major code change detected, manual code review (SAST - Manual) triggerd. Proceed?"
                ok "Approve"
                parameters {
                    string(name: 'ApprovalComment', defaultValue: 'Approved', description: 'Approval Comment.')
                }
            }
            steps {
                echo "Out-of-Band Activity - SAST Plus Manual triggered & approved with comment: {$ApprovalComment}."
            }
        }

        stage('SCA - NPM Audit') {
            when {
                expression { isSCAEnabled }
            }
            steps {
                echo 'Running NPM Audit'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/npm-audit/npm-audit-adapter.json --output npm-audit-adapter.json'
                sh 'curl https://raw.githubusercontent.com/synopsys-sig/io-client-adapters/eslint/npm-audit/npm-audit.sh --output npm-audit.sh'
                synopsysIO() {
                    sh 'io --stage execution --adapters npm-audit-adapter.json --state io_state.json'
                }
            }
        }

        stage('DAST Plus Manual') {
            when {
                expression { isDASTPlusMEnabled }
            }
            input {
                message "Major code change detected, manual threat-modeling (DAST - Manual) triggerd. Proceed?"
                ok "Approve"
                parameters {
                    string(name: 'ApprovalComment', defaultValue: 'Approved', description: 'Approval Comment.')
                }
            }
            steps {
                echo "Out-of-Band Activity - SAST Plus Manual triggered & approved with comment: {$ApprovalComment}."
            }
        }

        stage('IO - Workflow') {
            steps {
                echo 'Execute Workflow Stage'
                synopsysIO() {
                    sh 'io --stage workflow --state io_state.json'
                }
            }
        }
    }

    post {
        always {
            // Archive Results & Logs
            archiveArtifacts artifacts: 'npm-install.log', allowEmptyArchive: 'true'
            archiveArtifacts artifacts: 'npm-audit.log', allowEmptyArchive: 'true'
            archiveArtifacts artifacts: 'npm-eslint.log', allowEmptyArchive: 'true'

            // Remove the state json file as it has sensitive information
            sh 'rm io_state.json'
        }
    }
}
