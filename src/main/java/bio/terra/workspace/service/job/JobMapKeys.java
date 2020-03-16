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

  private String keyName;

  JobMapKeys(String keyName) {
    this.keyName = keyName;
  }

  public String getKeyName() {
    return keyName;
  }
}
