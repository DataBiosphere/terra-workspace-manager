package scripts.utils;

import bio.terra.workspace.model.GcpBigQueryDataTableAttributes;
import bio.terra.workspace.model.GcpBigQueryDatasetAttributes;
import bio.terra.workspace.model.GcpGcsBucketAttributes;
import bio.terra.workspace.model.GcpGcsObjectAttributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility functions for parsing resource identifiers, e.g.
 * "projects/my-project/datasets/my-dataset"
 */
public class ResourceNameUtils {

  public static final Pattern BQ_DATASET_PATTERN =
      Pattern.compile("^projects/([^/]+)/datasets/([^/]+)$");
  public static final Pattern BQ_TABLE_PATTERN =
      Pattern.compile("^projects/([^/]+)/datasets/([^/]+)/tables/(.+)$");
  public static final Pattern GCS_BUCKET_PATTERN = Pattern.compile("^gs://([^/]+)$");
  public static final Pattern GCS_OBJECT_PATTERN = Pattern.compile("^gs://([^/]+)/(.+)$");

  /**
   * Parse BigQuery dataset attributes from a fully-qualified GCP resource identifier string (e.g.
   * "projects/my-project/datasets/mydataset").
   *
   * <p>This only parses the resource identifier string, it does not check if the provided IDs are
   * real or valid.
   */
  public static GcpBigQueryDatasetAttributes parseBqDataset(String resourceIdentifier) {
    Matcher matcher = BQ_DATASET_PATTERN.matcher(resourceIdentifier);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Resource identifier "
              + resourceIdentifier
              + " does not match expected pattern for BQ dataset");
    }
    return new GcpBigQueryDatasetAttributes()
        .projectId(matcher.group(1))
        .datasetId(matcher.group(2));
  }

  /**
   * Parse BigQuery table attributes from a fully-qualified GCP resource identifier string (e.g.
   * "projects/my-project/datasets/mydataset/tables/mytable").
   *
   * <p>This only parses the resource identifier string, it does not check if the provided IDs are
   * real or valid.
   */
  public static GcpBigQueryDataTableAttributes parseBqTable(String resourceIdentifier) {
    Matcher matcher = BQ_TABLE_PATTERN.matcher(resourceIdentifier);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Resource identifier "
              + resourceIdentifier
              + " does not match expected pattern for BQ table");
    }
    return new GcpBigQueryDataTableAttributes()
        .projectId(matcher.group(1))
        .datasetId(matcher.group(2))
        .dataTableId(matcher.group(3));
  }

  /**
   * Parse GCS bucket attributes from a GCS URI (e.g. "gs://my-bucket").
   *
   * <p>This only parses the resource identifier string, it does not check if the provided IDs are
   * real or valid.
   */
  public static GcpGcsBucketAttributes parseGcsBucket(String resourceIdentifier) {
    Matcher matcher = GCS_BUCKET_PATTERN.matcher(resourceIdentifier);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Resource identifier "
              + resourceIdentifier
              + " does not match expected pattern for GCS bucket");
    }
    return new GcpGcsBucketAttributes().bucketName(matcher.group(1));
  }

  /**
   * Parse GCS object attributes from a GCS URI (e.g. "gs://my-bucket/somedir/myobject"). This will
   * return the full name of the object ("somedir/myobject" above), as GCS buckets don't actually
   * have directory structure.
   *
   * <p>This only parses the resource identifier string, it does not check if the provided IDs are
   * real or valid.
   */
  public static GcpGcsObjectAttributes parseGcsObject(String resourceIdentifier) {
    Matcher matcher = GCS_OBJECT_PATTERN.matcher(resourceIdentifier);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Resource identifier "
              + resourceIdentifier
              + " does not match expected pattern for GCS bucket");
    }
    return new GcpGcsObjectAttributes().bucketName(matcher.group(1)).fileName(matcher.group(2));
  }
}
