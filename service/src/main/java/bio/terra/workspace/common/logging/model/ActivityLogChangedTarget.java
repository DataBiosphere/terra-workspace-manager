package bio.terra.workspace.common.logging.model;

/** Type of targets workspace manager change activity can make changes to. */
public enum ActivityLogChangedTarget {
  APPLICATION,
  AZURE_CLOUD_CONTEXT,
  FOLDER,
  GCP_CLOUD_CONTEXT,
  RESOURCE,
  USER,
  WORKSPACE,
  POLICIES
}
