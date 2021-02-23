package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.service.datareference.model.DataReferenceType;

public enum WsmResourceType {
  GCS_BUCKET(
      DataReferenceType.GOOGLE_BUCKET,
      CloudPlatform.GCP), // a bucket potentially in GCS, Azure Blob Storage, or S3
  BIGQUERY_DATASET(DataReferenceType.BIG_QUERY_DATASET, CloudPlatform.GCP);

  private final DataReferenceType dataReferenceType;
  private final CloudPlatform cloudPlatform;

  WsmResourceType(DataReferenceType dataReferenceType, CloudPlatform cloudPlatform) {
    this.dataReferenceType = dataReferenceType;
    this.cloudPlatform = cloudPlatform;
  }

  public DataReferenceType toDataReferenceType() {
    return dataReferenceType;
  }

  public CloudPlatform getCloudPlatform() {
    return cloudPlatform;
  }
}
