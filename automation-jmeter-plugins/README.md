# automation-jmeter-plugins
This module holds the following Custom JMeter Functions developed by us.

### `OAuth2Token` - 

- String: Cloud Provider (`GCP`, `AWS`, `AZURE` - only `GCP` implementation exists for now)
- Env Var: `FIRECLOUD_SERVICE_ACCT_CREDS`: Service account key used for SAM User Authorization purpose
- Env Var: `SAM_USER`: Email account for OAuth2 Authorization

### Dependencies (see build.gradle)

- JMeter core library (`ApacheJMeter_core`)
- Google OAuth2 library `google-auth-library-oauth2-http`

### Build
Run `./gcloud_builds_submit.sh` to build the `jar` file and submit it to Google Storage bucket.

The `cloudbuild.yaml` defines the build steps.

For local development, make sure the application default credentials have access to
the storage bucket defined in `cloudbuild.yaml`. 

Substitute the project (`terra-kernel-k8s`) and bucket (`terra-kernel-k8s_cloudbuild`) names if needed (see `cloudbuild.yaml`, `gcloud_builds_submit.sh`).

### Deployment
Download the `automation-jmeter-plugins-${version}.jar` from the bucket (`terra-kernel-k8s_cloudbuild`) into the ${JMETER_HOME}/`lib/ext` directory of any worker machine.

### Usage 
#### `OAuth2Token` - 
Use this function in any JMeter setUp Thread Group to obtain a Bearer token property to run load tests.

- `${__OAuth2Token(GCP, FIRECLOUD_SERVICE_ACCT_CREDS, SAM_USER)`
