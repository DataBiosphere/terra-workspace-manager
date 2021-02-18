package bio.terra.workspace.service.resource.controlled;

public enum WsmResourceType {
  BUCKET, // a bucket potentially in GCS, Azure Blob Storage, or S3
  BIGQUERY_DATASET;
}
