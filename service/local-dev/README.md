This directory contains scripts for running continuous development builds. This
is not necessary for deployment, but it can be helpful for developing faster.

The provided setup script clones the terra-helm and terra-helmfile git repos,
and templates in the desired Terra environment/k8s namespace to target.
If you need to pull changes to either terra-helm or terra-helmfile, rerun this script.

You may need to use gcloud to provide GCR
 credentials with `gcloud auth configure-docker`. Finally, run local-run.sh with
  your target environment as the first argument:

```
./setup_local_env.sh <environment>
```

Optionally, you can provide a branch of [terra-helm](https://github.com/broadinstitute/terra-helm) and/or [terra-helmfile](https://github.com/broadinstitute/terra-helmfile) to use if you are testing PRs to either. They default to master. A git protocol (`ssh` or `http`) can optionally be specified as a fourth argument to the script.
```
./setup_local_env.sh <environment> [<terra-helm ref>] [<terra-helmfile ref>] [ssh|http]
```

Ensure your `gcloud` state is pointing to the correct cluster:
```
gcloud container clusters get-credentials terra-integration --zone us-central1-a --project terra-kernel-k8s
```

You can now push to the specified environment by running

```
skaffold run
```

or by using IntelliJ's Cloud Code integration, which will auto-detect the
generated skaffold.yaml file.
