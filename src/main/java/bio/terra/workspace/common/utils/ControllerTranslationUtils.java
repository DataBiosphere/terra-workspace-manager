package bio.terra.workspace.common.utils;

import bio.terra.workspace.generated.model.DataReferenceInfo;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import bio.terra.workspace.service.datareference.model.BigQueryDatasetReference;
import bio.terra.workspace.service.datareference.model.GoogleBucketReference;
import bio.terra.workspace.service.datareference.model.ReferenceObject;
import bio.terra.workspace.service.datareference.model.SnapshotReference;

/**
 * Utility functions for translating between interface objects and internal objects. Generally,
 * these should only be called by Controllers, as services and other internal layers should not know
 * about interface objects.
 */
public class ControllerTranslationUtils {

  public static ReferenceObject referenceInfoToReferenceObject(
      ReferenceTypeEnum referenceTypeEnum, DataReferenceInfo referenceInfo) {
    switch (referenceTypeEnum) {
      case DATA_REPO_SNAPSHOT:
        return SnapshotReference.create(
            referenceInfo.getDataRepoSnapshot().getInstanceName(),
            referenceInfo.getDataRepoSnapshot().getSnapshot());
      case GOOGLE_BUCKET:
        return GoogleBucketReference.create(referenceInfo.getGoogleBucket().getBucketName());
      case BIG_QUERY_DATASET:
        return BigQueryDatasetReference.create(
            referenceInfo.getBigQueryDataset().getProjectId(),
            referenceInfo.getBigQueryDataset().getDatasetId());
      default:
        throw new IllegalArgumentException("Unrecognized reference type");
    }
  }
}
