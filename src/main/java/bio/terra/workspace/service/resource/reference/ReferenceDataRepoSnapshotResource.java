package bio.terra.workspace.service.resource.reference;

import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshot;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotReference;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import java.util.Optional;
import java.util.UUID;

public class ReferenceDataRepoSnapshotResource extends ReferenceResource {
  private final String instanceName;
  private final String snapshotId;

  /**
   * Constructor for serialized form for Stairway use
   *
   * @param workspaceId workspace unique identifier
   * @param resourceId resource unique identifier
   * @param name name - may be null
   * @param description description - may be null
   * @param cloningInstructions cloning instructions
   * @param instanceName name of the data repository instance (e.g., "terra")
   * @param snapshotId name of the snapshot
   */
  @JsonCreator
  public ReferenceDataRepoSnapshotResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("instanceName") String instanceName,
      @JsonProperty("snapshotId") String snapshotId) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
    this.instanceName = instanceName;
    this.snapshotId = snapshotId;
  }

  /**
   * Constructor from database metadata
   *
   * @param dbResource database form of resources
   */
  public ReferenceDataRepoSnapshotResource(DbResource dbResource) {
    super(dbResource);
    if (dbResource.getResourceType() != WsmResourceType.DATA_REPO_SNAPSHOT) {
      throw new InvalidMetadataException("Expected DATA_REPO_SNAPSHOT");
    }
    ReferenceDataRepoSnapshotAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferenceDataRepoSnapshotAttributes.class);
    this.instanceName = attributes.getInstanceName();
    this.snapshotId = attributes.getSnapshotId();
  }

  public String getInstanceName() {
    return instanceName;
  }

  public String getSnapshotId() {
    return snapshotId;
  }

  public ApiDataRepoSnapshotReference toApiModel() {
    return new ApiDataRepoSnapshotReference()
        .metadata(super.toApiMetadata())
        .snapshot(
            new ApiDataRepoSnapshot().instanceName(getInstanceName()).snapshot(getSnapshotId()));
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.DATA_REPO_SNAPSHOT;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ReferenceDataRepoSnapshotAttributes(getInstanceName(), getSnapshotId()));
  }

  @Override
  public void validate() {
    super.validate();
    if (Strings.isNullOrEmpty(getInstanceName()) || Strings.isNullOrEmpty(getSnapshotId())) {
      throw new MissingRequiredFieldException(
          "Missing required field for ReferenceApiDataRepoSnapshotAttributes.");
    }
  }

  public static ReferenceDataRepoSnapshotResource.Builder builder() {
    return new ReferenceDataRepoSnapshotResource.Builder();
  }

  public static class Builder {
    private UUID workspaceId;
    private UUID resourceId;
    private String name;
    private String description;
    private CloningInstructions cloningInstructions;
    private String instanceName;
    private String snapshotId;

    public ReferenceDataRepoSnapshotResource.Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public ReferenceDataRepoSnapshotResource.Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public ReferenceDataRepoSnapshotResource.Builder name(String name) {
      this.name = name;
      return this;
    }

    public ReferenceDataRepoSnapshotResource.Builder description(String description) {
      this.description = description;
      return this;
    }

    public ReferenceDataRepoSnapshotResource.Builder cloningInstructions(
        CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
      return this;
    }

    public Builder instanceName(String instanceName) {
      this.instanceName = instanceName;
      return this;
    }

    public Builder snapshotId(String snapshotId) {
      this.snapshotId = snapshotId;
      return this;
    }

    public ReferenceDataRepoSnapshotResource build() {
      // On the create path, we can omit the resourceId and have it filled in by the builder.
      return new ReferenceDataRepoSnapshotResource(
          workspaceId,
          Optional.ofNullable(resourceId).orElse(UUID.randomUUID()),
          name,
          description,
          cloningInstructions,
          instanceName,
          snapshotId);
    }
  }
}
