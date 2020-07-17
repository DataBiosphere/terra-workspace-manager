This directory contains scripts for running continuous development of performance test builds.
This is not necessary for deployment, but it can be helpful for developing faster.
The provided setup script clones the terra-helm and terra-helmfile git repos,
and templates in the desired Terra environment/k8s namespace to target.
If you need to pull changes to either terra-helm or terra-helmfile, rerun this script.

NOTE: The terra-helm-local folder consists of artifacts for launching performance tests in k8s.
It is currently not part of the terra-helm repo but will be in the future.

The local-perf provides an example on **Distributed load testing using GKE** as described in this article:
https://cloud.google.com/solutions/distributed-load-testing-using-gke

To use this, first ensure Skaffold is installed on your local machine 
(available at https://skaffold.dev/). 

> Older versions of Skaffold (v1.4.0 and earlier) do not have support for Helm 3 and will fail to deploy your 
changes. If you're seeing errors like `UPGRADE FAILED: "(Release name)" has no 
deployed releases`, try updating Skaffold.

You may need to use gcloud to provide GCR
 credentials with `gcloud auth configure-docker`. Finally, run local-run.sh with
  your target environment as the first argument:

```
./setup_local_env.sh <environment>
```

You can now push to the specified environment by running

```
skaffold run
```

or by using IntelliJ's Cloud Code integration, which will auto-detect the 
generated skaffold.yaml file.
