This directory contains scripts for running continuous development builds. This is not necessary for deployment, but it can be helpful for developing faster.
The provided setup script clones the terra-workspace-manager-config git repo, points it to the local version of this repo (rather than a published version), and points skaffold to the -config repo's kustomize.
If you need to pull changes to the -config repo, just delete your local terra-workspace-manager-config directory and rerun the script.

To use this, first ensure Skaffold is installed on your local machine (available at https://skaffold.dev/). You may need to use gcloud to provide GCR credentials with `gcloud auth configure-docker`. Finally, run local-run.sh with your target environment as the first argument:

```
./setup_local_env.sh <environment>
```

You can now push to the specified environment by running

```
skaffold run
```
or by using IntelliJ's Cloud Code integration, which will auto-detect the generated skaffold.yaml file.
