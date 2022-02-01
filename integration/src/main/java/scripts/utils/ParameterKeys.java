package scripts.utils;

/**
 * Constants used for keys in TestScript parameters. This is the source-of-truth for test parameter
 * values, test configs should match the values here.
 */
public class ParameterKeys {

  public static final String STATUS_CHECK_DELAY_PARAMETER = "status-check-delay";
  public static final String SPEND_PROFILE_PARAMETER = "spend-profile-id";
  public static final String DATA_REPO_SNAPSHOT_PARAMETER = "snapshot-id";
  public static final String DATA_REPO_ALTERNATE_SNAPSHOT_PARAMETER = "snapshot-id-2";
  public static final String DATA_REPO_INSTANCE_PARAMETER = "data-repo-instance";
  public static final String REFERENCED_GCS_BUCKET = "gcs-bucket";
  public static final String REFERENCED_GCS_UNIFORM_BUCKET = "gcs-uniform-bucket";
  public static final String REFERENCED_GCS_OBJECT = "gcs-object";
  public static final String REFERENCED_GCS_FOLDER = "gcs-folder";
  public static final String REFERENCED_BQ_DATASET = "bq-dataset";
  public static final String REFERENCED_BQ_TABLE = "bq-table";
  public static final String REFERENCED_BQ_TABLE_FROM_ALTERNATE_DATASET = "bq-table-2";
  public static final String REFERENCED_SSH_GIT_REPO = "git-repo";
}
