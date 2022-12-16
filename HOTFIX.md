# Hotfix Process

## Preparing The Release Candidate

1) Clone the repository, if necessary
2) Checkout the version-tagged branch of the repository you need to fix. The tags are the same
as the software version numbers. You will typically want the version running in production.
```shell script
git checkout <tag>
```
3) Create a hotfix branch from that version. Make the name start with "hotfix", so that
the automatic version process will make a hotfix version.
```shell script
git checkout -b hotfix-PF-1234
``` 
4) Make your fix and test it in the usual way. When it is ready, push it to the repo.
5) Manually run the `tag-publish` workflow to build and publish your hotfix. You can accept
the default for `bump` input. Since you are in a hotfix branch it will be ignored. Supply your
branch name for the `branch` input.

## Delivering The Hotfix

1) `gcloud auth login` as an account that has push access to gcr.io/broad-dsp-gcr-public, likely your @broadinstitute.org account

2) Run `gcloud auth configure-docker`

3) Build and push the image that you would like to use for your hotfix:

```sh
./gradlew jib --image=gcr.io/broad-dsp-gcr-public/terra-workspace-manager:hotfix-<DATE>
```

4) Follow Step 1 in terra-helmfile/[HOTFIX.md](https://github.com/broadinstitute/terra-helmfile/blob/master/docs/HOTFIX.md)


5) Follow Step 2 in terra-helmfile/[HOTFIX.md](https://github.com/broadinstitute/terra-helmfile/blob/master/docs/HOTFIX.md), except instead of running ArgoCD to sync the deployment, run the [gke-deploy Jenkins job](https://fc-jenkins.dsp-techops.broadinstitute.org/job/gke-deploy/) with `project` set to `workspacemanager`. Do this for `dev` and `staging`. Using this Jenkins deploy job will ensure that the tests run against the hotfix.


6) Follow Step 3 in terra-helmfile/[HOTFIX.md](https://github.com/broadinstitute/terra-helmfile/blob/master/docs/HOTFIX.md)


Should the hotfix process further deviate from the general procedure outlined in terra-helmfile, this document should be updated to highlight those differences.
