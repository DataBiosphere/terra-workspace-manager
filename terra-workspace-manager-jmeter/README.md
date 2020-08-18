# terra-workspace-manager-jmeter
This module holds the Workspace Manager JMeter Scripts for load testing.

To run test in different environments (including per-developer environments). 
Please refer to the jmeter-director-worker deployment definitions in this repo].

### Env Vars - 

- FIRECLOUD_SERVICE_ACCT_CREDS: Service account key used for SAM User Authorization purpose
- SAM_USER: Email account for OAuth2 Authorization
- WSM_SERVER: Workspace Manager Server, e.g. workspace.dsde-staging.broadinstitute.org
- WSM_PORT: Workspace Manager Server Port (e.g. 80 for http, 443 for https)
- WSM_PROTOCOL: http or https
- INFLUXDB_WSM_URL: Influx DB URL for connecting to performance results database (demo only)
- INFLUXDB_WSM_MEASUREMENT: Influx DB table for storing performance results (demo only)

### Dependencies (see build.gradle)

- JMeter core library (ApacheJMeter_core)
- JMeter function library (ApacheJMeter_funtions)
- JMeter http library (ApacheJMeter_http)
- JMeter java library (ApacheJMeter_java)
- jmeter-plugins-functions
- Custom JMeter plugin functions by us (see module automation-jmeter-plugins)

### Deploying test to JMeter director/worker Kubernetes cluster
- $namespace is the namespace of JMeter director/worker infrastructure
- $director_pod is JMeter director pod
- $test_name is name of the test (e.g. CreateworkspaceSimulation)

kubectl -n $namespace exec -ti $director_pod -- /bin/bash /load_test "$test_name"

NB: Command will eventually be wrapped in a shell script