package scripts.utils;

import java.util.Map;

/** Utility functions for reading test parameters from configurations. */
public class ParameterUtils {

  public static String getParamOrThrow(Map<String, String> params, String expectedKey) {
    if (params == null || !params.containsKey(expectedKey)) {
      throw new IllegalArgumentException("Test missing required parameter: " + expectedKey);
    }
    return params.get(expectedKey);
  }

  public static String getSpendProfile(Map<String, String> params) {
    return getParamOrThrow(params, ParameterKeys.SPEND_PROFILE_PARAMETER);
  }

  public static String getDataRepoInstance(Map<String, String> params) {
    return getParamOrThrow(params, ParameterKeys.DATA_REPO_INSTANCE_PARAMETER);
  }

  public static String getDataRepoSnapshot(Map<String, String> params) {
    return getParamOrThrow(params, ParameterKeys.DATA_REPO_SNAPSHOT_PARAMETER);
  }
}
