lThis directory contains scripts for running continuous development builds. This 
is not necessary for deployment, but it can be helpful for developing faster.
The provided setup script clones the terra-helm and terra-helmfile git repos,
and templates in the desired Terra environment/k8s namespace to target.
If you need to pull changes to either terra-helm or terra-helmfile, rerun this script.

To use this, first ensure [Helm](https://helm.sh/docs/intro/install/#from-homebrew-macos) and [Skaffold](https://skaffold.dev/) are installed on your local machine. 

> Older versions of Skaffold (v1.4.0 and earlier) do not have support for Helm 3 and will fail to deploy your 
changes. If you're seeing errors like `UPGRADE FAILED: "(Release name)" has no 
deployed releases`, try updating Skaffold.

You may need to use gcloud to provide GCR
 credentials with `gcloud auth configure-docker`. Finally, run local-run.sh with
  your target environment as the first argument:

```
./setup_local_env.sh <environment>
```

Optionally, you can provide a branch of [terra-helm](https://github.com/broadinstitute/terra-helm) and/or [terra-helmfile](https://github.com/broadinstitute/terra-helmfile) to use if you are testing PRs to either. They default to master.
```
./setup_local_env.sh <environment> <terra-helm ref> <terra-helmfile ref>
```

You can now push to the specified environment by running

```
skaffold run
```

or by using IntelliJ's Cloud Code integration, which will auto-detect the 
generated skaffold.yaml file.
