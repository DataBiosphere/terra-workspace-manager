# Stairway Experiments
This document describes the setup and results for a manual verification
and validation of Stairway recovery across multiple Workspace Manager
instances.

## 1. Single-instance pod kill
The purpose is to see the response, if any, when a pod is killed during a flight. THis uses
the `DelayHook` to give time to kill a flight in `kubectl`.

### Code Changes
* In `JobService`, install DelayHook into `bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight` at step 1 with 3 minute duration

### Deployment
Setup the local envirnoment for `terra-jcarlton`:
```bash
./setup_local_env.sh jcarlton master master ssh
```

Build image and deploy with `skaffold run`.

This should succeed with 
```
Waiting for deployments to stabilize...
 - terra-jcarlton:deployment/workspacemanager-deployment: waiting for rollout to finish: 0 of 1 updated replicas are available...
 - terra-jcarlton:deployment/workspacemanager-deployment is ready.
Deployments stabilized in 1 minute 24.928 seconds
You can also run [skaffold run --tail] to get the logs
```
### Environment
* `jcarlton` personal environment
* 1 relplica per helm file
```json
{
  "name": "workspace-jcarlton",
  "description": "Workspace jcarlton personal environment",

  "samUri": "https://sam.dsde-dev.broadinstitute.org",
  "datarepoUri": "https://jade.datarepo-dev.broadinstitute.org/",
  "workspaceManagerUri": "https://workspace.jcarlton.integ.envs.broadinstitute.org/",

  "cluster": {},
  "deploymentScript": {},
  "testRunnerServiceAccountFile": "delegate-user-sa.json",

  "skipDeployment": true,
  "skipKubernetes": true
}
```

### Invocation
Use a dedicated test suite
```json
{
  "name": "BasicIntegration",
  "description": "Integration tests that do not require a K8s setup; for PR merge testing. Please add tests to FullIntegration when you add them here.",
  "serverSpecificationFile": "workspace-jcarlton.json",
  "testConfigurationFiles": [
    "integration/WorkspaceLifecycle.json"
  ]
}
```

### Database
Use DD Connection script like `~/tools/connect-db.sh jcarlton stairway`

```
psql "host=127.0.0.1 port=5435 sslmode=disable dbname=workspace-stairway user=workspace-stairway"

Fetching cluster endpoint and auth data.
kubeconfig entry generated for terra-integration.
sqlinstance is workspace-db-jcarlton-5a65b1a9838a91cb
proxying to terra-kernel-k8s:us-central1:workspace-db-jcarlton-5a65b1a9838a91cb
```

* Use IntelliJ to look at `flight`, `flightInput`, and `flightLog`
    * initially empty

### Running the test
Use `gw runTest --args="suites/kill-pod-jcarlton.json /tmp/TR"` to kick off test

First run ends early (with all zero timings) due to delay.

### Logs
Start with https://console.cloud.google.com/logs/query?project=terra-kernel-k8s

A good starting query is
```
resource.labels.project_id="terra-kernel-k8s"
resource.labels.cluster_name="terra-integration"
resource.labels.namespace_name="terra-jcarlton"
resource.labels.location="us-central1-a"
resource.labels.container_name="workspacemanager"
-httpRequest.requestUrl="/status"
-httpRequest.requestUrl="/version"
```
Make sure to grab the pod name from `resource.labels.pod_name`. It's the one
we need to kill.

Some details
```json
resource: {
type: "k8s_container"
labels: {
project_id: "terra-kernel-k8s"
namespace_name: "terra-jcarlton"
pod_name: "workspacemanager-deployment-5ffc97854c-9vbt4"
container_name: "workspacemanager"
location: "us-central1-a"
cluster_name: "terra-integration"
}
}
timestamp: "2021-04-05T14:03:19.046Z"
severity: "INFO"
labels: {
k8s-pod/app_kubernetes_io/managed-by: "Helm"
compute.googleapis.com/resource_name: "gke-terra-integration-default-v2-95d71f95-4apb"
k8s-pod/app_kubernetes_io/component: "workspacemanager"
k8s-pod/pod-template-hash: "5ffc97854c"
k8s-pod/app_kubernetes_io/instance: "workspacemanager"
k8s-pod/helm_sh/chart: "workspacemanager-0.28.0"
k8s-pod/app_kubernetes_io/part-of: "terra"
k8s-pod/app_kubernetes_io/name: "workspacemanager"
k8s-pod/deployment: "workspacemanager-deployment"
}
logName: "projects/terra-kernel-k8s/logs/stdout"
trace: "projects/terra-kernel-k8s/traces/bd46ec786a17de60eaadc68e78cd6b1f"
```

## Test attempt 1
Though I didn't kill the pod, the delay apparently killed the test, and put the
flight into a state needing recovery.
```2021-04-05 10:14:20.889 EDT
"Disowned 1 flights for stairway: workspacemanager-deployment-5ffc97854c-9vbt4"
Info
2021-04-05 10:14:20.964 EDT
"Found ready flights: 1"
Info
2021-04-05 10:14:21.328 EDT
"Queued message. Id: 2236461439804609; Msg: {"type":{"version":"1","messageEnum":"QUEUE_MESSAGE_READY"},"flightId":"7348bc13-2255-4a50-85b5-fcb5534674cb"}"
Info
2021-04-05 10:14:23.723 EDT
"Received message: {"type":{"version":"1","messageEnum":"QUEUE_MESSAGE_READY"},"flightId":"7348bc13-2255-4a50-85b5-fcb5534674cb"}"
Info
2021-04-05 10:14:23.749 EDT
"Stairway workspacemanager-deployment-68f9b944d5-9kgzp taking ownership of flight 7348bc13-2255-4a50-85b5-fcb5534674cb"
Warning
2021-04-05 10:14:23.766 EDT
"Unexpected exception dispatching messages - continuing
bio.terra.stairway.exception.MakeFlightException: Failed to make a flight from class 'class bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight'
	at bio.terra.stairway.StairwayFlightFactory.makeFlight(StairwayFlightFactory.java:30)
	at bio.terra.stairway.StairwayFlightFactory.makeFlightFromName(StairwayFlightFactory.java:41)
	at bio.terra.stairway.Stairway.resume(Stairway.java:682)
	at bio.terra.stairway.QueueMessageReady.process(QueueMessageReady.java:24)
	at bio.terra.stairway.QueueMessage.processMessage(QueueMessage.java:43)
	at bio.terra.stairway.Queue.dispatchMessages(Queue.java:148)
	at bio.terra.stairway.WorkQueueListener.run(WorkQueueListener.java:39)
	at java.base/java.lang.Thread.run(Unknown Source)
Caused by: java.lang.reflect.InvocationTargetException: null
	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance(Unknown Source)
	at java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(Unknown Source)
	at java.base/java.lang.reflect.Constructor.newInstance(Unknown Source)
	at bio.terra.stairway.StairwayFlightFactory.makeFlight(StairwayFlightFactory.java:23)
	... 7 common frames omitted
Caused by: java.lang.ClassCastException: Found value 'MC_WORKSPACE' is not an instance of type bio.terra.workspace.service.workspace.model.WorkspaceStage
	at bio.terra.stairway.FlightMap.get(FlightMap.java:89)
	at bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight.<init>(WorkspaceCreateFlight.java:22)
	... 12 common frames omitted
" 
Info
2021-04-05 10:16:04.348 EDT
"Received message: {"type":{"version":"1","messageEnum":"QUEUE_MESSAGE_READY"},"flightId":"7348bc13-2255-4a50-85b5-fcb5534674cb"}"
Info
2021-04-05 10:16:04.356 EDT
"Stairway workspacemanager-deployment-68f9b944d5-9kgzp did not resume flight: 7348bc13-2255-4a50-85b5-fcb5534674cb"
```

Root cause is ` Found value 'MC_WORKSPACE' is not an instance of type bio.terra.workspace.service.workspace.model.WorkspaceStage`
Enums have recently been discovered to be broken, so this is a known issue.

This test left us with flight ID `7348bc13-2255-4a50-85b5-fcb5534674cb` in the `RUNNING` state.
## Test attempt 2
In this attempt, we created a flight with ID `882af56c-5fa5-4838-a08d-3d2bb5df0dfe`,
which was delayed for three minutes, resumed, and run to completion successfully.
No attempt to kill the pod yet.

## Test attempt 3
STarted 10:38 AM on 4/5/21
`$ kubectl delete pod workspacemanager-deployment-68f9b944d5-9kgzp --namespace=terra-jcarlton`
TestRunner test was incomplete.
Flight `327ae7fe-5225-4299-ab0f-009619b2be63` had already resumed.
