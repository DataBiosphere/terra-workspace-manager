# Personal Test Environments
Personallly provisioned test environments, and the steps to create and manage them, are described here.
## Background
### Use Case
Personal Kubernetes environments allow realistic testing of deployed services without interfering
with shared environments. This enables faster development cycles, as code can be tested without
waiting for a release, and without risk of breaking colleagues' environments.

### Where they Live
A personal environment is contained by a K8s [namespace](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)
named for a developer, sharing a larger cluster in the GCP
project [terra-kernel-k8s](https://console.cloud.google.com/home/dashboard?project=terra-kernel-k8s)
called 
[terra-integration](https://console.cloud.google.com/kubernetes/clusters/details/us-central1-a/terra-integration?project=terra-kernel-k8s&tab=details&persistent_volumes_tablesize=50&storage_class_tablesize=50&nodes_tablesize=50&node_pool_tablesize=10).
Deployments target this namespace and allow `kubectl` commands to be narrowly focused. The deployment
consists of a single
[pod](https://kubernetes.io/docs/concepts/workloads/pods/) and [service](https://kubernetes.io/docs/concepts/services-networking/service/)
in the case of Workspace Manager, though the system is more flexible than that.

## Setup

## Administration
Interaction with Kubernetes is most often accomplished with `kubectl`. This is a local utility that is packaged
with Kubernetes. Type `kubectl version` to make sure it's installed.
```shell script
$ kubectl version
```
```text
Client Version: version.Info{Major:"1", Minor:"19", GitVersion:"v1.19.3", GitCommit:"1e11e4a2108024935ecfcb2912226cedeafd99df", GitTreeState:"clean", BuildDate:"2020-10-14T12:50:19Z", GoVersion:"go1.15.2", Compiler:"gc", Platform:"darwin/amd64"}
Server Version: version.Info{Major:"1", Minor:"17+", GitVersion:"v1.17.14-gke.400", GitCommit:"b00b302d1576bfe28ae50661585cc516fda2227e", GitTreeState:"clean", BuildDate:"2020-11-19T09:22:49Z", GoVersion:"go1.13.15b4", Compiler:"gc", Platform:"linux/amd64"}
```
 To connect `kubectl` to the cluster, you must first update `kubeconfig` with the proper credentials. 
`gcloud container` has a `clusters get-credentials` option to support exactly this use case. In our case
we need to specify both the project and zone as well as the cluster name:  
```shell script
$ gcloud container clusters get-credentials terra-integration --zone us-central1-a --project terra-kernel-k8s
```
```text
Fetching cluster endpoint and auth data.
kubeconfig entry generated for terra-integration.
```
