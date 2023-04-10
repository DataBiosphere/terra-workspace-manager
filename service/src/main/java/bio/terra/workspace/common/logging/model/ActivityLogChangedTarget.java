package bio.terra.workspace.common.logging.model;

/** Type of targets workspace manager change activity can make changes to. */
public enum ActivityLogChangedTarget {
  APPLICATION,
  FOLDER,
  RESOURCE,
  USER,
  WORKSPACE,
  POLICIES,

  // Cloud specific
  GCP_CLOUD_CONTEXT,
  AZURE_CLOUD_CONTEXT,
  AWS_CLOUD_CONTEXT
}
