This directory contains scripts for deploying JMeter director/worker infrastructure for load testing. 

To use this, first ensure [Helm](https://helm.sh/docs/intro/install/#from-homebrew-macos) and [Skaffold](https://skaffold.dev/) are installed on your local machine. 

> Older versions of Skaffold (v1.4.0 and earlier) do not have support for Helm 3 and will fail to deploy your 
changes. If you're seeing errors like `UPGRADE FAILED: "(Release name)" has no 
deployed releases`, try updating Skaffold.

You may need to use gcloud to provide GCR
 credentials with `gcloud auth configure-docker`. Finally, run local-run.sh with
  your target environment as the first argument:

You can now push to the specified environment by running.
Currently, the namespace defaults to terra-jmeter.

```
skaffold run
```

or by using IntelliJ's Cloud Code integration, which will auto-detect the 
generated skaffold.yaml file.
