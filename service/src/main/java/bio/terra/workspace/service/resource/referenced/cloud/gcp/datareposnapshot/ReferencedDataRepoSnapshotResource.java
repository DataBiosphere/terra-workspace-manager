package bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotAttributes;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceUnion;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResource;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ReferencedDataRepoSnapshotResource extends ReferencedResource {
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
   * @param resourceLineage resource lineage
   */
  @JsonCreator
  public ReferencedDataRepoSnapshotResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("instanceName") String instanceName,
      @JsonProperty("snapshotId") String snapshotId,
      @JsonProperty("resourceLineage") List<ResourceLineageEntry> resourceLineage) {
    super(workspaceId, resourceId, name, description, cloningInstructions, resourceLineage);
    this.instanceName = instanceName;
    this.snapshotId = snapshotId;
    validate();
  }

  /**
   * Constructor from database metadata
   *
   * @param dbResource database form of resources
   */
  public ReferencedDataRepoSnapshotResource(DbResource dbResource) {
    super(dbResource);
    if (dbResource.getResourceType() != WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT) {
      throw new InvalidMetadataException("Expected referenced DATA_REPO_SNAPSHOT");
    }
    ReferencedDataRepoSnapshotAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferencedDataRepoSnapshotAttributes.class);
    this.instanceName = attributes.getInstanceName();
    this.snapshotId = attributes.getSnapshotId();
    validate();
  }

  public static ReferencedDataRepoSnapshotResource.Builder builder() {
    return new ReferencedDataRepoSnapshotResource.Builder();
  }

  public String getInstanceName() {
    return instanceName;
  }

  public String getSnapshotId() {
    return snapshotId;
  }

  public ApiDataRepoSnapshotAttributes toApiAttributes() {
    return new ApiDataRepoSnapshotAttributes()
        .instanceName(getInstanceName())
        .snapshot(getSnapshotId());
  }

  public ApiDataRepoSnapshotResource toApiResource() {
    return new ApiDataRepoSnapshotResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T castByEnum(WsmResourceType expectedType) {
    if (getResourceType() != expectedType) {
      throw new BadRequestException(String.format("Resource is not a %s", expectedType));
    }
    return (T) this;
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.DATA_REPO_SNAPSHOT;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ReferencedDataRepoSnapshotAttributes(getInstanceName(), getSnapshotId()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    return new ApiResourceAttributesUnion().gcpDataRepoSnapshot(toApiAttributes());
  }

  @Override
  public ApiResourceUnion toApiResourceUnion() {
    return new ApiResourceUnion().gcpDataRepoSnapshot(toApiResource());
  }

  @Override
  public void validate() {
    super.validate();
    if (Strings.isNullOrEmpty(getInstanceName()) || Strings.isNullOrEmpty(getSnapshotId())) {
      throw new MissingRequiredFieldException(
          "Missing required field for ReferenceDataRepoSnapshotAttributes.");
    }
  }

  @Override
  public boolean checkAccess(FlightBeanBag context, AuthenticatedUserRequest userRequest) {
    DataRepoService dataRepoService = context.getDataRepoService();
    return dataRepoService.snapshotReadable(instanceName, snapshotId, userRequest);
  }

  public Builder toBuilder() {
    return builder()
        .cloningInstructions(getCloningInstructions())
        .description(getDescription())
        .instanceName(getInstanceName())
        .name(getName())
        .snapshotId(getSnapshotId())
        .resourceId(getResourceId())
        .workspaceId(getWorkspaceId())
        .resourceLineage(getResourceLineage());
  }

  public static class Builder {
    private CloningInstructions cloningInstructions;
    private String description;
    private String instanceName;
    private String name;
    private String snapshotId;
    private UUID resourceId;
    private UUID workspaceId;
    private List<ResourceLineageEntry> resourceLineage;

    public ReferencedDataRepoSnapshotResource.Builder workspaceId(UUID workspaceUuid) {
      this.workspaceId = workspaceUuid;
      return this;
    }

    public ReferencedDataRepoSnapshotResource.Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public ReferencedDataRepoSnapshotResource.Builder name(String name) {
      this.name = name;
      return this;
    }

    public ReferencedDataRepoSnapshotResource.Builder description(String description) {
      this.description = description;
      return this;
    }

    public ReferencedDataRepoSnapshotResource.Builder cloningInstructions(
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

    public Builder resourceLineage(List<ResourceLineageEntry> resourceLineage) {
      this.resourceLineage = resourceLineage;
      return this;
    }

    public ReferencedDataRepoSnapshotResource build() {
      // On the create path, we can omit the resourceId and have it filled in by the builder.
      return new ReferencedDataRepoSnapshotResource(
          workspaceId,
          Optional.ofNullable(resourceId).orElse(UUID.randomUUID()),
          name,
          description,
          cloningInstructions,
          instanceName,
          snapshotId,
          resourceLineage);
    }
  }
}
