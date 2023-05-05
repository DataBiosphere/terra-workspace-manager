package bio.terra.workspace.common.logging.model;

/** Type of targets workspace manager change activity can make changes to. */
public enum ActivityLogChangedTarget {
  APPLICATION,
  FOLDER,
  RESOURCE(/*isResource=*/ true),
  REFERENCED_ANY_GIT_REPO(/*isResource=*/ true),
  REFERENCED_ANY_TERRA_WORKSPACE(/*isResource=*/ true),
  REFERENCED_ANY_DATA_REPO_SNAPSHOT(/*isResource=*/ true),
  CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE(/*isResource=*/ true),
  REFERENCED_GCP_GCS_BUCKET(/*isResource=*/ true),
  CONTROLLED_GCP_GCS_BUCKET(/*isResource=*/ true),
  REFERENCED_GCP_GCS_OBJECT(/*isResource=*/ true),
  CONTROLLED_GCP_BIG_QUERY_DATASET(/*isResource=*/ true),
  REFERENCED_GCP_BIG_QUERY_DATASET(/*isResource=*/ true),
  REFERENCED_GCP_BIG_QUERY_DATA_TABLE(/*isResource=*/ true),
  CONTROLLED_AZURE_DISK(/*isResource=*/ true),
  CONTROLLED_AZURE_VM(/*isResource=*/ true),
  CONTROLLED_AZURE_STORAGE_CONTAINER(/*isResource=*/ true),
  CONTROLLED_AZURE_BATCH_POOL(/*isResource=*/ true),
  // AWS
  CONTROLLED_AWS_S3_STORAGE_FOLDER(/*isResource=*/ true),
  CONTROLLED_FLEXIBLE_RESOURCE(/*isResource=*/ true),

  USER,
  WORKSPACE,
  POLICIES,

  // Cloud specific
  GCP_CLOUD_CONTEXT,
  AZURE_CLOUD_CONTEXT,
  AWS_CLOUD_CONTEXT;

  private final boolean isResource;

  ActivityLogChangedTarget(boolean isResource) {
    this.isResource = isResource;
  }

  ActivityLogChangedTarget() {
    this.isResource = false;
  }

  public boolean isResource() {
    return isResource;
  }
}
