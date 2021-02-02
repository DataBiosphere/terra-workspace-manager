package bio.terra.workspace.service.datareference.utils;

import bio.terra.cloudres.google.bigquery.DatasetCow;
import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import bio.terra.workspace.service.datareference.model.BigQueryDatasetReference;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.model.GoogleBucketReference;
import bio.terra.workspace.service.datareference.model.ReferenceObject;
import bio.terra.workspace.service.datareference.model.SnapshotReference;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.DatasetId;
import com.google.cloud.storage.StorageException;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A collection of validation functions for data references. */
@Component
public class DataReferenceValidationUtils {

  private final DataRepoService dataRepoService;
  private final CrlService crlService;

  private static final Logger logger = LoggerFactory.getLogger(DataReferenceValidationUtils.class);

  /**
   * Names must be 1-63 characters long, and may consist of alphanumeric characters and underscores
   * (but may not start with an underscore). These restrictions match TDR snapshot name restrictions
   * as we often expect users to use snapshot names as reference names, though this isn't required.
   */
  public static final Pattern NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-zA-Z0-9][_a-zA-Z0-9]{0,62}$");

  public static void validateReferenceName(String name) {
    if (StringUtils.isEmpty(name) || !NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      throw new InvalidDataReferenceException(
          "Invalid reference name specified. Name must be 1 to 63 alphanumeric characters or underscores, and cannot start with an underscore.");
    }
  }

  @Autowired
  public DataReferenceValidationUtils(DataRepoService dataRepoService, CrlService crlService) {
    this.dataRepoService = dataRepoService;
    this.crlService = crlService;
  }

  /**
   * Validates a referenceObject, with specific validation rules varying based on the actual type of
   * the object.
   */
  public void validateReferenceObject(
      ReferenceObject reference,
      DataReferenceType referenceType,
      AuthenticatedUserRequest userReq) {

    switch (referenceType) {
      case DATA_REPO_SNAPSHOT:
        validateSnapshotReference((SnapshotReference) reference, userReq);
        return;
      case GOOGLE_BUCKET:
        validateGoogleBucket((GoogleBucketReference) reference, userReq);
        return;
      case BIG_QUERY_DATASET:
        validateBigQueryDataset((BigQueryDatasetReference) reference, userReq);
        return;
      default:
        throw new InvalidDataReferenceException(
            "Invalid reference type specified. Valid types include: "
                + DataReferenceType.DATA_REPO_SNAPSHOT.toString());
    }
  }

  private void validateSnapshotReference(SnapshotReference ref, AuthenticatedUserRequest userReq) {
    if (StringUtils.isBlank(ref.instanceName()) || StringUtils.isBlank(ref.snapshot())) {
      throw new InvalidDataReferenceException(
          "Invalid Data Repo Snapshot identifier: "
              + "instanceName and snapshot must both be provided.");
    }
    if (!dataRepoService.snapshotExists(ref.instanceName(), ref.snapshot(), userReq)) {
      logger.warn(
          String.format(
              "Snapshot %s not found in data repo instance %s",
              ref.snapshot(), ref.instanceName()));
      throw new InvalidDataReferenceException(
          "The given snapshot could not be found in the Data Repo instance provided."
              + " Verify that your reference was correctly defined and the instance is correct");
    }
  }

  /**
   * Validates that a bucket exists and the user has GET access to it. This makes no other
   * assumptions about the bucket.
   */
  private void validateGoogleBucket(GoogleBucketReference ref, AuthenticatedUserRequest userReq) {
    try {
      // StorageCow.get() returns null if the bucket does not exist or a user does not have access,
      // which fails validation.
      BucketCow bucket = crlService.createStorageCow(userReq).get(ref.bucketName());
      if (bucket == null) {
        logger.warn(String.format("Bucket %s not found", ref.bucketName()));
        throw new InvalidDataReferenceException(
            "Could not access specified GCS bucket. Ensure the name is correct and that you have access.");
      }
    } catch (StorageException e) {
      throw new InvalidDataReferenceException("Error while trying to access GCS bucket", e);
    }
  }

  /**
   * Validates that a BQ dataset exists and the user has GET access to it. This makes no other
   * assumptions about the dataset.
   */
  private void validateBigQueryDataset(
      BigQueryDatasetReference ref, AuthenticatedUserRequest userReq) {
    try {
      DatasetId datasetId = DatasetId.of(ref.projectId(), ref.datasetName());
      // BigQueryCow.get() returns null if the bucket does not exist or a user does not have access,
      // which fails validation.
      DatasetCow dataset = crlService.createBigQueryCow(userReq).getDataset(datasetId);
      if (dataset == null) {
        logger.warn(
            String.format(
                "Dataset %s not found in project %s", ref.datasetName(), ref.projectId()));
        throw new InvalidDataReferenceException(
            "Could not access specified BigQuery dataset. Ensure the name and GCP project are correct and that you have access.");
      }
    } catch (BigQueryException e) {
      throw new InvalidDataReferenceException("Error while trying to access BigQuery dataset", e);
    }
  }
}
