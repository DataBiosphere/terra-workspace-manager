package bio.terra.workspace.service.resource;

import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.referenced.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

public enum WsmResourceType {
  DATA_REPO_SNAPSHOT(
      CloudPlatform.GCP, "DATA_REPO_SNAPSHOT", ReferencedDataRepoSnapshotResource.class, null),
  GCS_BUCKET(
      CloudPlatform.GCP,
      "GCS_BUCKET",
      ReferencedGcsBucketResource.class,
      ControlledGcsBucketResource.class),
  BIG_QUERY_DATASET(
      CloudPlatform.GCP, "BIG_QUERY_DATASET", ReferencedBigQueryDatasetResource.class, null);

  private final CloudPlatform cloudPlatform;
  private final String dbString; // serialized form of the resource type
  private final Class<? extends ReferencedResource> referenceClass;
  private final Class<? extends ControlledResource> controlledClass;

  WsmResourceType(
      CloudPlatform cloudPlatform,
      String dbString,
      Class<? extends ReferencedResource> referenceClass,
      Class<? extends ControlledResource> controlledClass) {
    this.cloudPlatform = cloudPlatform;
    this.dbString = dbString;
    this.referenceClass = referenceClass;
    this.controlledClass = controlledClass;
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

  public CloudPlatform getCloudPlatform() {
    return cloudPlatform;
  }

  public Class<? extends ReferencedResource> getReferenceClass() {
    return referenceClass;
  }

  public Class<? extends ControlledResource> getControlledClass() {
    return controlledClass;
  }

  public String toSql() {
    return dbString;
  }
}
