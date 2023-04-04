package bio.terra.workspace.common.logging.model;

/** Type of targets workspace manager change activity can make changes to. */
public enum ActivityLogChangedTarget {
  WORKSPACE,

  GCP_CLOUD_CONTEXT,
  AZURE_CLOUD_CONTEXT,
  AWS_CLOUD_CONTEXT,

  FOLDER,
  RESOURCE,
  APPLICATION,
  USER,

  POLICIES
}
