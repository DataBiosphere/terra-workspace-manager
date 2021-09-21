package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.generated.model.ApiCloneResourceResult;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;

/** Internal wrapper type for {@link ApiCloneResourceResult}. */
public enum WsmCloneResourceResult {
  SUCCEEDED("SUCCEEDED", ApiCloneResourceResult.SUCCEEDED),
  FAILED("FAILED", ApiCloneResourceResult.FAILED),
  SKIPPED("SKIPPED", ApiCloneResourceResult.SKIPPED);

  private final String value;
  private final ApiCloneResourceResult apiValue;

  WsmCloneResourceResult(String value, ApiCloneResourceResult apiValue) {
    this.value = value;
    this.apiValue = apiValue;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonIgnore
  public ApiCloneResourceResult toApiModel() {
    return apiValue;
  }

  @JsonCreator
  public static WsmCloneResourceResult fromValue(String text) {
    for (WsmCloneResourceResult b : WsmCloneResourceResult.values()) {
      if (String.valueOf(b.value).equals(text)) {
        return b;
      }
    }
    return null;
  }
}
