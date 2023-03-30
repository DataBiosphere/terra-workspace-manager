# Decomposed OpenAPI Structure

The goal of decomposing the OpenAPI YAML file is to simplify adding new resources. Each
resource can be added nearly independently.

In the initial implementation, all files in all of the subdirectories of `src/` are scanned by the tool
and merged into a single YAML output file. That output is processed by the swagger codegen
tool to generate the client and server code.

## Directory Structure

The directory structure is setup like this:
- parts
- resources
- common
  - schemas.yaml - shared schema elements
  - schemas_azure.yaml - Azure specific schema elements
  - responses.yaml - shared response elements
  - responses_azure.yaml - Azure specific response elements
  - parameters.yaml - shared parameter elements

### Parts
This directory contains all the paths and the high-level schema material.

### Resources
This directory contains the resource and attribute definitions for all resource types.
Many resources share their attribute and resource definitions between a controlled and a
referenced version. Those definitions are also referenced by the enumeration
endpoints. Therefore, we place all resource attribute and definition files here.

In the future, we may drive auto-generation of enumeration unions by scanning this
directory. Files should be named:
```
resource_{resource-type}
```
For example, the file describing a GCP GCS bucket would be `resource_gcp_gcs_bucket.yaml`.

### Common
This directory contains three files that correspond to the three main parts of the
`components` section of the OpenAPI document. These are elements that are shared by more
than one "part" and are not specific to a resource.

