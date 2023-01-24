package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

public enum CloudPlatform {
  GCP("GCP", ApiCloudPlatform.GCP, "gcp"),
  AZURE("AZURE", ApiCloudPlatform.AZURE, "azure"),
  /** The resource does not have to be strongly associated with one cloud platform. */
  ANY("ANY", null, null);

  private final String dbString;
  private final ApiCloudPlatform apiCloudPlatform;

  private final String tpsString;

  CloudPlatform(String dbString, ApiCloudPlatform apiCloudPlatform, String tpsString) {
    this.dbString = dbString;
    this.apiCloudPlatform = apiCloudPlatform;
    this.tpsString = tpsString;
  }

  public String toSql() {
    return dbString;
  }

  public String toTps() {
    if (tpsString == null) {
      throw new InternalLogicException(
          String.format("Cloud platform %s does not have a TPS equivalent", dbString));
    }
    return tpsString;
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
