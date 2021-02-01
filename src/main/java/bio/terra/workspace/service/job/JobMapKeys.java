package bio.terra.workspace.service.job;

public enum JobMapKeys {
  // parameters for all flight types
  DESCRIPTION("description"),
  REQUEST("request"),
  RESPONSE("response"),
  STATUS_CODE("status_code"),
  AUTH_USER_INFO("auth_user_info"),
  SUBJECT_ID("subjectId"),

  // parameter for the job
  FLIGHT_CLASS("flight_class");

  private final String keyName;

  JobMapKeys(String keyName) {
    this.keyName = keyName;
  }

  public String getKeyName() {
    return keyName;
  }

  public static boolean isRequiredKey(String keyName) {
    return keyName.equals(JobMapKeys.DESCRIPTION.getKeyName())
        || keyName.equals(JobMapKeys.REQUEST.getKeyName())
        || keyName.equals(JobMapKeys.AUTH_USER_INFO.getKeyName())
        || keyName.equals((JobMapKeys.SUBJECT_ID.getKeyName()));
  }
}
