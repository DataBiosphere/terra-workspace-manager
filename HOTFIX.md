# Hotfix Process

1) Build the image that you would like to use for your hotfix:

```sh
./gradlew jibDockerBuild --image=gcr.io/terra-kernel-k8s/terra-workspace-manager:hotfix-<DATE>
```

2) Push the image that you've created to GCR:

```sh
docker push gcr.io/terra-kernel-k8s/terra-workspace-manager:hotfix-<DATE>
```

3) Follow Step 1 in terra-helmfile/[HOTFIX.md](https://github.com/broadinstitute/terra-helmfile/blob/master/docs/HOTFIX.md)


4) Follow Step 2 in terra-helmfile/[HOTFIX.md](https://github.com/broadinstitute/terra-helmfile/blob/master/docs/HOTFIX.md), except instead of running ArgoCD to sync the deployment, run the [gke-deploy Jenkins job](https://fc-jenkins.dsp-techops.broadinstitute.org/job/gke-deploy/) with `project` set to `workspacemanager`. Do this for `dev` and `staging`. Using this Jenkins deploy job will ensure that the tests run against the hotfix.


5) Follow Step 3 in terra-helmfile/[HOTFIX.md](https://github.com/broadinstitute/terra-helmfile/blob/master/docs/HOTFIX.md)


Should the hotfix process further deviate from the general procedure outlined in terra-helmfile, this document should be updated to highlight those differences.
