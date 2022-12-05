
# terra-workspace-manager

* [Overview](#overview)
* [WSM Client](#wsm-client)
    * [Usage (Gradle)](#usage-gradle)

This repository holds the MC Terra Workspace Manager (WSM) service, client, and
integration test projects.

## Overview

WSM provides workspaces; contexts for holding the work of
individuals and teams. A _workspace_ has members that are granted some role on the
workspace (OWNER, READER, WRITER). The members can create and manage _resources_ in the
workspace. There are two types of resources:
- _controlled resources_ are cloud resources (e.g., buckets) whose attributes,
  permissions, and lifecycle are controlled by the Workspace Manager. Controlled resources
  are created and managed using Workspace Manager APIs.
- _referenced resources_ are cloud resources that are independent of the
  Workspace Manager. A workspace may hold a reference to such a resource. The Workspace
  Manager has no role in managing the resource’s lifecycle or attributes.

Resources have unique names within the workspace, allowing users of the workspace to
locate and refer to them in a consistent way, whether they are controlled or referenced.

The resources in a workspace may reside on different clouds. Users may create one _cloud
context_ for each cloud platform where they have controlled or referenced resources.

Workspace Manager provides the minimum interface to allow it to control permissions and
lifecycle of controlled resources. All other access, in particular data reading and
writing, are done using the native cloud APIs.

Controlled resources may be _shared_ or _private_. Shared resources are accessible to
workspace members with their workspace role. That is, if you have READER on the workspace,
then you can read the resource (however that is defined for the specific resource); if you
have WRITER on the workspace, then you can write the resource.

Private resources are available to a single member of the workspace. At the present time,
a private resource is available only to its creator.

WSM has latent support for _applications_. No applications exist at this time. The concept
is that an application is a distinguished service account. Owners of the workspace can
control which applications are allowed access to the workspace. If an application is given
access, then it can create application-owned resources. The goal is to allow applications
to create constellations of resources that support the application, and not let them be
messed with by workspace READERS and WRITERS.

## WSM Client
Workspace Manager publishes an API client library generated from its OpenAPI Spec v3
interface definition.

### Usage (Gradle)

Include the Broad Artifactory repositories:
```gradle
repositories {
    maven {
        url "https://broadinstitute.jfrog.io/broadinstitute/libs-snapshot-local/"
    }
}
```

Add a dependency like
```gradle
implementation(group: 'bio.terra', name: 'workspace-manager-client', version: 'x.x.x')
```
See [settings.gradle](settings.gradle) for the latest version information.
