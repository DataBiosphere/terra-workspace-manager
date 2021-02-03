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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A collection of validation functions for data references. */
@Component
public class DataReferenceValidationUtils {

  private final DataRepoService dataRepoService;
  private final CrlService crlService;

  /**
   * Names must be 1-63 characters long, and may consist of alphanumeric characters and underscores
   * (but may not start with an underscore). These restrictions match TDR snapshot name restrictions
   * as we often expect users to use snapshot names as reference names, though this isn't required.
   */
  public static final Pattern NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-zA-Z0-9][_a-zA-Z0-9]{0,62}$");

  /**
   * GCS bucket name validation is somewhat complex due to rules about usage of "." and restricted
   * names like "goog", but as a baseline they must be 3-222 characters long using lowercase
   * letters, numbers, dashes, underscores, and dots, and must start and end with a letter or
   * number. Matching this pattern is necessary but not sufficient for a valid bucket name.
   */
  public static final Pattern BUCKET_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[a-z0-9][-_.a-z0-9]{1,220}[a-z0-9]$");

  /**
   * BigQuery datasets must be 1-1024 characters, using letters (upper or lowercase), numbers, and
   * underscores.
   */
  public static final Pattern BQ_DATASET_NAME_VALIDATION_PATTERN =
      Pattern.compile("^[_a-zA-Z0-9]{1,1024}$");

  public static void validateReferenceName(String name) {
    if (StringUtils.isEmpty(name) || !NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      throw new InvalidDataReferenceException(
          "Invalid reference name specified. Name must be 1 to 63 alphanumeric characters or underscores, and cannot start with an underscore.");
    }
  }

  public static void validateBucketName(String name) {
    if (StringUtils.isEmpty(name) || !BUCKET_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      throw new InvalidDataReferenceException(
          "Invalid GCS bucket name specified. Names must be 3-222 lowercase letters, numbers, dashes, and underscores. See Google documentation for the full specification.");
    }
  }

  public static void validateBqDatasetName(String name) {
    if (StringUtils.isEmpty(name) || !BQ_DATASET_NAME_VALIDATION_PATTERN.matcher(name).matches()) {
      throw new InvalidDataReferenceException(
          "Invalid BQ dataset name specified. Name must be 1 to 1024 alphanumeric characters or underscores.");
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
      throw new InvalidDataReferenceException(
          String.format(
              "Snapshot %s could not be found in Data Repo instance %s. Verify that your reference was correctly defined and the instance is correct",
              ref.snapshot(), ref.instanceName()));
    }
  }

  /**
   * Validates that a bucket exists and the user has GET access to it. This makes no other
   * assumptions about the bucket.
   */
  private void validateGoogleBucket(GoogleBucketReference ref, AuthenticatedUserRequest userReq) {
    DataReferenceValidationUtils.validateBucketName(ref.bucketName());
    try {
      // StorageCow.get() returns null if the bucket does not exist or a user does not have access,
      // which fails validation.
      BucketCow bucket = crlService.createStorageCow(userReq).get(ref.bucketName());
      if (bucket == null) {
        throw new InvalidDataReferenceException(
            String.format(
                "Could not access GCS bucket %s. Ensure the name is correct and that you have access.",
                ref.bucketName()));
      }
    } catch (StorageException e) {
      throw new InvalidDataReferenceException(
          String.format("Error while trying to access GCS bucket %s", ref.bucketName()), e);
    }
  }

  /**
   * Validates that a BQ dataset exists and the user has GET access to it. This makes no other
   * assumptions about the dataset.
   */
  private void validateBigQueryDataset(
      BigQueryDatasetReference ref, AuthenticatedUserRequest userReq) {
    DataReferenceValidationUtils.validateBqDatasetName(ref.datasetName());
    try {
      DatasetId datasetId = DatasetId.of(ref.projectId(), ref.datasetName());
      // BigQueryCow.get() returns null if the bucket does not exist or a user does not have access,
      // which fails validation.
      DatasetCow dataset = crlService.createBigQueryCow(userReq).getDataset(datasetId);
      if (dataset == null) {
        throw new InvalidDataReferenceException(
            String.format(
                "Could not access BigQuery dataset %s in project %s. Ensure the name and GCP project are correct and that you have access.",
                ref.datasetName(), ref.projectId()));
      }
    } catch (BigQueryException e) {
      throw new InvalidDataReferenceException("Error while trying to access BigQuery dataset", e);
    }
  }
}
