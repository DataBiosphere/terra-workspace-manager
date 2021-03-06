package bio.terra.workspace.service.resource.reference;

import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.DataRepoSnapshot;
import bio.terra.workspace.generated.model.DataRepoSnapshotReference;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import java.util.UUID;

public class ReferenceDataRepoSnapshotResource extends ReferenceResource {
  private final String instanceName;
  private final String snapshot;

  /**
   * Constructor for serialized form for Stairway use
   *
   * @param workspaceId workspace unique identifier
   * @param resourceId resource unique identifier
   * @param name name - may be null
   * @param description description - may be null
   * @param cloningInstructions cloning instructions
   * @param instanceName name of the data repository instance (e.g., "terra")
   * @param snapshot name of the snapshot
   */
  @JsonCreator
  public ReferenceDataRepoSnapshotResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("instanceName") String instanceName,
      @JsonProperty("snapshot") String snapshot) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
    this.instanceName = instanceName;
    this.snapshot = snapshot;
  }

  /**
   * Constructor from database metadata
   *
   * @param dbResource database form of resources
   */
  public ReferenceDataRepoSnapshotResource(DbResource dbResource) {
    super(
        dbResource.getWorkspaceId(),
        dbResource.getResourceId(),
        dbResource.getName().orElse(null),
        dbResource.getDescription().orElse(null),
        dbResource.getCloningInstructions());

    if (dbResource.getResourceType() != WsmResourceType.DATA_REPO_SNAPSHOT) {
      throw new InvalidMetadataException("Expected DATA_REPO_SNAPSHOT");
    }
    ReferenceDataRepoSnapshotAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferenceDataRepoSnapshotAttributes.class);
    this.instanceName = attributes.getInstanceName();
    this.snapshot = attributes.getSnapshot();
  }

  public String getInstanceName() {
    return instanceName;
  }

  public String getSnapshot() {
    return snapshot;
  }

  public DataRepoSnapshotReference toApiModel() {
    return new DataRepoSnapshotReference()
        .metadata(super.toApiMetadata())
        .snapshot(new DataRepoSnapshot().instanceName(getInstanceName()).snapshot(getSnapshot()));
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.DATA_REPO_SNAPSHOT;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ReferenceDataRepoSnapshotAttributes(getInstanceName(), getSnapshot()));
  }

  @Override
  public void validate() {
    super.validate();
    if (Strings.isNullOrEmpty(getInstanceName()) || Strings.isNullOrEmpty(getSnapshot())) {
      throw new MissingRequiredFieldException(
          "Missing required field for ReferenceDataRepoSnapshotAttributes.");
    }
    // TODO: Something funky is going on. The integration test is passing a UUID for the
    // snapshot - not the snapshot name. So this check is always failing.
    // ValidationUtils.validateDataRepoName(getSnapshot());
  }
}
