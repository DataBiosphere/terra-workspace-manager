This directory contains scripts for running continuous development builds. This
is not necessary for deployment, but it can be helpful for developing faster.
It clones the terra-helmfile git repo to get the chart and values files for deploying to
your personal environment.

Invoke the script as follows:
```
./setup_local_env.sh <environment> [ssh|http] [<terra-helmfile ref>] 
```

Where `<environment>` is the name of your personal environment.

Optionally, you can specify the git protocol (`ssh` or `http`) for retrieving terra-helmfile. It
defaults to http.

Optionally, you can provide a branch of [terra-helmfile](https://github.com/broadinstitute/terra-helmfile)
to use if you are testing PRs. It defaults to the master branch. 

The script generates a helm values file, cleverly named `values.yaml`. You can edit that
values file to change Spring configuration parameters. Our setup allows feeding environment
variables into the target environment by adding things like this:
```yaml
env:
  FEATURE_AZURE_ENABLED: true
```

You may need to use gcloud to provide GCR credentials with `gcloud auth configure-docker`.

Ensure your `gcloud` state is pointing to the correct cluster:
```
gcloud container clusters get-credentials terra-integration --zone us-central1-a --project terra-kernel-k8s
```

You can now push to the specified environment by running
```
skaffold run
```




