package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.service.datareference.model.DataReferenceType;

// TODO: should this merge with DataReferenceType? References and controlled resources support
// different types.
public enum WsmResourceType {
  BUCKET(DataReferenceType.GOOGLE_BUCKET), // a bucket potentially in GCS, Azure Blob Storage, or S3
  BIGQUERY_DATASET(DataReferenceType.BIG_QUERY_DATASET);

  private DataReferenceType dataReferenceType;

  WsmResourceType(DataReferenceType dataReferenceType) {
    this.dataReferenceType = dataReferenceType;
  }

  DataReferenceType toDataReferenceType() {
    return dataReferenceType;
  }
}
