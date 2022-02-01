package scripts.utils;

import bio.terra.workspace.model.GcpBigQueryDataTableAttributes;
import bio.terra.workspace.model.GcpBigQueryDatasetAttributes;
import bio.terra.workspace.model.GcpGcsBucketAttributes;
import bio.terra.workspace.model.GcpGcsObjectAttributes;
import bio.terra.workspace.model.GitRepoAttributes;
import java.util.Map;

/** Utility functions for reading test parameters from configurations. */
public class ParameterUtils {

  public static String getParamOrThrow(Map<String, String> params, String expectedKey) {
    if (params == null || !params.containsKey(expectedKey)) {
      throw new IllegalArgumentException("Test missing required parameter: " + expectedKey);
    }
    return params.get(expectedKey);
  }

  // Utils to keep the SetParams test methods shorter.

  public static String getSpendProfile(Map<String, String> params) {
    return getParamOrThrow(params, ParameterKeys.SPEND_PROFILE_PARAMETER);
  }

  public static String getDataRepoInstance(Map<String, String> params) {
    return getParamOrThrow(params, ParameterKeys.DATA_REPO_INSTANCE_PARAMETER);
  }

  public static String getDataRepoSnapshot(Map<String, String> params) {
    return getParamOrThrow(params, ParameterKeys.DATA_REPO_SNAPSHOT_PARAMETER);
  }

  public static GcpGcsBucketAttributes getUniformBucketReference(Map<String, String> params) {
    return ResourceNameUtils.parseGcsBucket(
        getParamOrThrow(params, ParameterKeys.REFERENCED_GCS_UNIFORM_BUCKET));
  }

  public static GcpGcsBucketAttributes getFineGrainedBucketReference(Map<String, String> params) {
    return ResourceNameUtils.parseGcsBucket(
        getParamOrThrow(params, ParameterKeys.REFERENCED_GCS_BUCKET));
  }

  public static GcpGcsObjectAttributes getGcsFileReference(Map<String, String> params) {
    return ResourceNameUtils.parseGcsObject(
        getParamOrThrow(params, ParameterKeys.REFERENCED_GCS_OBJECT));
  }

  public static GcpGcsObjectAttributes getGcsFolderReference(Map<String, String> params) {
    return ResourceNameUtils.parseGcsObject(
        getParamOrThrow(params, ParameterKeys.REFERENCED_GCS_FOLDER));
  }

  public static GcpBigQueryDatasetAttributes getBigQueryDatasetReference(
      Map<String, String> params) {
    return ResourceNameUtils.parseBqDataset(
        getParamOrThrow(params, ParameterKeys.REFERENCED_BQ_DATASET));
  }

  public static GcpBigQueryDataTableAttributes getBigQueryDataTableReference(
      Map<String, String> params) {
    return ResourceNameUtils.parseBqTable(
        getParamOrThrow(params, ParameterKeys.REFERENCED_BQ_TABLE));
  }

  public static GitRepoAttributes getSshGitRepoReference(Map<String, String> params) {
    return new GitRepoAttributes()
        .gitRepoUrl(getParamOrThrow(params, ParameterKeys.REFERENCED_SSH_GIT_REPO));
  }
}
