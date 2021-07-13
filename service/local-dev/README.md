This directory contains scripts for running continuous development builds. This
is not necessary for deployment, but it can be helpful for developing faster.
The provided setup script clones the terra-helmfile git repo and renders
Kubernetes manifests for the target Terra environment/k8s namespace.
If you need to pull changes to terra-helmfile, rerun this script.

You may need to use gcloud to provide GCR
 credentials with `gcloud auth configure-docker`. Finally, run local-run.sh with
  your target environment as the first argument:

```
./setup_local_env.sh <environment>
```

Optionally, you can provide a branch of [terra-helmfile](https://github.com/broadinstitute/terra-helmfile) to use if you are testing a PR. (The script defaults to master).
```
./setup_local_env.sh <environment> <terra-helmfile ref>
```

You can now push to the specified environment by running

```
skaffold run
```

or by using IntelliJ's Cloud Code integration, which will auto-detect the
generated skaffold.yaml file.
