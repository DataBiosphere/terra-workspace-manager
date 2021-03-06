package bio.terra.workspace.service.resource;

import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.reference.ReferenceBigQueryDatasetResource;
import bio.terra.workspace.service.resource.reference.ReferenceDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.reference.ReferenceGcsBucketResource;
import bio.terra.workspace.service.resource.reference.ReferenceResource;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

public enum WsmResourceType {
  DATA_REPO_SNAPSHOT(
      CloudPlatform.GCP,
      "DATA_REPO_SNAPSHOT",
      ReferenceDataRepoSnapshotResource.class,
      ControlledGcsBucketResource.class),
  GCS_BUCKET(CloudPlatform.GCP, "GCS_BUCKET", ReferenceGcsBucketResource.class, null),
  BIG_QUERY_DATASET(
      CloudPlatform.GCP, "BIG_QUERY_DATASET", ReferenceBigQueryDatasetResource.class, null);

  private final CloudPlatform cloudPlatform;
  private final String dbString; // serialized form of the resource type
  private final Class<? extends ReferenceResource> referenceClass;
  private final Class<? extends ControlledResource> controlledClass;

  WsmResourceType(
      CloudPlatform cloudPlatform,
      String dbString,
      Class<? extends ReferenceResource> referenceClass,
      Class<? extends ControlledResource> controlledClass) {
    this.cloudPlatform = cloudPlatform;
    this.dbString = dbString;
    this.referenceClass = referenceClass;
    this.controlledClass = controlledClass;
  }

  public CloudPlatform getCloudPlatform() {
    return cloudPlatform;
  }

  public Class<? extends ReferenceResource> getReferenceClass() {
    return referenceClass;
  }

  public Class<? extends ControlledResource> getControlledClass() {
    return controlledClass;
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
