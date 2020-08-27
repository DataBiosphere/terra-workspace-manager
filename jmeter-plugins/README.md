# jmeter-plugins
This module holds the following Custom JMeter Functions developed by Broad Institute.

### `XCorrelationIDHeader` -

### Dependencies (see build.gradle)

- JMeter core library (`ApacheJMeter_core`)
- Hashids

### Build
Run `./gcloud_builds_submit.sh` to build the `jar` file and submit it to Google Storage bucket.

The `cloudbuild.yaml` defines the build steps.

For local development, make sure the application default credentials have access to
the storage bucket defined in `cloudbuild.yaml`.

Substitute the project (`terra-kernel-k8s`) and bucket (`terra-kernel-k8s_cloudbuild`) names if needed (see `cloudbuild.yaml`, `gcloud_builds_submit.sh`).

### Deployment
Download the `jmeter-plugins-${version}.jar` from the bucket (`terra-kernel-k8s_cloudbuild`) into the ${JMETER_HOME}/`lib/ext` directory of any worker machine.

### Usage
#### `XCorrelationIDHeader` -
Use this function in any JMeter HTTP Header Manager to generate a unique Correlation ID for MDC log tracing.

- `${__XCorrelationIDHeader('WorkspaceManager:CreateWorkspace')`