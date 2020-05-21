This directory contains scripts for running continuous development builds. This 
is not necessary for deployment, but it can be helpful for developing faster.
The provided setup script clones the terra-helm and terra-helmfile git repos,
and templates in the desired Terra environment/k8s namespace to target.
If you need to pull changes to either terra-helm or terra-helmfile, rerun this script.

To use this, first ensure Skaffold is installed on your local machine (available at https://skaffold.dev/). You may need to use gcloud to provide GCR credentials with `gcloud auth configure-docker`. Finally, run local-run.sh with your target environment as the first argument:

```
./setup_local_env.sh <environment>
```

You can now push to the specified environment by running

```
skaffold run
```

or by using IntelliJ's Cloud Code integration, which will auto-detect the 
generated skaffold.yaml file.

In order to use the swagger-ui functionality to execute requests, you'll need to
locally modify the API definition at `src/resources/api/service_openapi.yaml` by
adding your url prefix to the `servers` list. Currently, url prefixes are
`/{environment name}-{service name}`. 

For example, the `terra-workspace-manager` 
service instance in the `dev` environment would have prefix 
`/dev-terra-workspace-manager`.
