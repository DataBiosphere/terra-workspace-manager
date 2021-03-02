package bio.terra.workspace.service.resource;

import bio.terra.workspace.service.workspace.model.CloudPlatform;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

public enum WsmResourceType {
  DATA_REPO_SNAPSHOT(CloudPlatform.GCP, "DATA_REPO_SNAPSHOT", true, false),
  GCS_BUCKET(CloudPlatform.GCP, "GCS_BUCKET", true, true),
  BIG_QUERY_DATASET(CloudPlatform.GCP, "BIG_QUERY_DATASET", true, true);

  private final CloudPlatform cloudPlatform;
  private final String dbString; // serialized form of the resource type
  private final boolean referenceSupported;
  private final boolean controlledSupported;

  WsmResourceType(
      CloudPlatform cloudPlatform,
      String dbString,
      boolean referenceSupported,
      boolean controlledSupported) {
    this.cloudPlatform = cloudPlatform;
    this.dbString = dbString;
    this.referenceSupported = referenceSupported;
    this.controlledSupported = controlledSupported;
  }

  public CloudPlatform getCloudPlatform() {
    return cloudPlatform;
  }

  public boolean supportsReference() {
    return referenceSupported;
  }

  public boolean supportsControlled() {
    return controlledSupported;
  }

  public String toSql() {
    return dbString;
  }

  public static WsmResourceType fromSql(String dbString) {
    for (WsmResourceType value : values()) {
      if (StringUtils.equals(value.dbString, dbString)) {
        return value;
      }
    }
    throw new SerializationException(
        "Deeserialization failed: no matching resource type for " + dbString);
  }
}
