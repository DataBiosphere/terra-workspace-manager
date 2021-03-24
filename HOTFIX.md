# Hotfix Process

1) `gcloud auth login` as an account that has push access to gcr.io/terra-kernel-k8s, likely your @broadinstitute.org account

2) Run `gcloud auth configure-docker`

3) Build and push the image that you would like to use for your hotfix:

```sh
./gradlew jib --image=gcr.io/terra-kernel-k8s/terra-workspace-manager:hotfix-<DATE>
```

4) Follow Step 1 in terra-helmfile/[HOTFIX.md](https://github.com/broadinstitute/terra-helmfile/blob/master/docs/HOTFIX.md)


5) Follow Step 2 in terra-helmfile/[HOTFIX.md](https://github.com/broadinstitute/terra-helmfile/blob/master/docs/HOTFIX.md), except instead of running ArgoCD to sync the deployment, run the [gke-deploy Jenkins job](https://fc-jenkins.dsp-techops.broadinstitute.org/job/gke-deploy/) with `project` set to `workspacemanager`. Do this for `dev` and `staging`. Using this Jenkins deploy job will ensure that the tests run against the hotfix.


6) Follow Step 3 in terra-helmfile/[HOTFIX.md](https://github.com/broadinstitute/terra-helmfile/blob/master/docs/HOTFIX.md)


Should the hotfix process further deviate from the general procedure outlined in terra-helmfile, this document should be updated to highlight those differences.
