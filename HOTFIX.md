# Hotfix Process

1) Build the image that you would like to use for your hotfix:

`./gradlew jibDockerBuild --image=gcr.io/terra-kernel-k8s/terra-workspace-manager:hotfix-<DATE> -Djib.console=plain`

2) Push the image that you've created to GCR:

`docker push gcr.io/terra-kernel-k8s/terra-workspace-manager:hotfix-<DATE>`

3) To apply the hotfix, see the [HOTFIX.md](https://github.com/broadinstitute/terra-helmfile/blob/master/docs/HOTFIX.md) documentation in [terra-helmfile](https://github.com/broadinstitute/terra-helmfile) for the general MC-Terra hotfix procedure, which applies to Workspace Manager. Use the image that you built above when applying the hotfix.

Should the hotfix process deviate from the general procedure outlined in terra-helmfile, this document should be updated to highlight those differences.
