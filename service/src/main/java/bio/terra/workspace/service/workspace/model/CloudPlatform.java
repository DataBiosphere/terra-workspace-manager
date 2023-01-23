package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

public enum CloudPlatform {
  GCP("GCP", ApiCloudPlatform.GCP),
  AZURE("AZURE", null),
  /** The resource does not have to be strongly associated with one cloud platform. */
  ANY("ANY", null);

  private final String dbString;
  private final ApiCloudPlatform apiCloudPlatform;

  CloudPlatform(String dbString, ApiCloudPlatform apiCloudPlatform) {
    this.dbString = dbString;
    this.apiCloudPlatform = apiCloudPlatform;
  }

  public String toSql() {
    return dbString;
  }

  public ApiCloudPlatform toApiModel() {
    return apiCloudPlatform;
  }

  public static CloudPlatform fromApiCloudPlatform(ApiCloudPlatform apiCloudPlatform) {
    for (CloudPlatform value : values()) {
      if (StringUtils.equals(value.dbString, apiCloudPlatform.name())) {
        return value;
      }
    }
    throw new BadRequestException(
        String.format("Unknown Api cloud platform %s", apiCloudPlatform.name()));
  }

  public static CloudPlatform fromSql(String dbString) {
    for (CloudPlatform value : values()) {
      if (StringUtils.equals(value.dbString, dbString)) {
        return value;
      }
    }
    throw new SerializationException(
        "Deserialization failed: no matching cloud platform for " + dbString);
  }
}
