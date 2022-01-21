def call() {
    pipeline {
        stages {
            stage('SAST Plus Manual') {
                when {
                    expression { isSASTPlusMEnabled }
                }
                input {
                    message 'Major code change detected, manual code review (SAST - Manual) triggerd. Proceed?'
                    ok 'Approve'
                    parameters {
                        string(name: 'ApprovalComment', defaultValue: 'Approved', description: 'Approval Comment.')
                    }
                }
                steps {
                    echo "Out-of-Band Activity - SAST Plus Manual triggered & approved with comment: {$ApprovalComment}."
                }
            }
        }
    }
}