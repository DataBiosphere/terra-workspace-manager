package bio.terra.workspace.service.resource.referenced.terra.workspace;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceUnion;
import bio.terra.workspace.generated.model.ApiTerraWorkspaceAttributes;
import bio.terra.workspace.generated.model.ApiTerraWorkspaceResource;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReferencedTerraWorkspaceResource extends ReferencedResource {
  private final UUID referencedWorkspaceId;

  /**
   * Constructor for serialized form for Stairway use and used by the builder
   *
   * @param workspaceId workspace unique identifier
   * @param resourceId resource unique identifier
   * @param name name - may be null
   * @param description description - may be null
   * @param cloningInstructions cloning instructions
   * @param referencedWorkspaceId workspace uuid that this referenced resource points to
   * @param resourceLineage resource lineage
   */
  @JsonCreator
  public ReferencedTerraWorkspaceResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("referencedWorkspaceId") UUID referencedWorkspaceId,
      @JsonProperty("resourceLineage") List<ResourceLineageEntry> resourceLineage,
      @JsonProperty("properties") Map<String, String> properties) {
    super(
        workspaceId,
        resourceId,
        name,
        description,
        cloningInstructions,
        resourceLineage,
        properties);
    this.referencedWorkspaceId = referencedWorkspaceId;
    validate();
  }

  /**
   * Constructor from database metadata
   *
   * @param dbResource database form of resources
   */
  public ReferencedTerraWorkspaceResource(DbResource dbResource) {
    super(dbResource);
    if (dbResource.getResourceType() != WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE) {
      throw new InvalidMetadataException("Expected REFERENCED_TERRA_WORKSPACE");
    }

    ReferencedTerraWorkspaceAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferencedTerraWorkspaceAttributes.class);
    this.referencedWorkspaceId = attributes.getReferencedWorkspaceId();
    validate();
  }

  private ReferencedTerraWorkspaceResource(Builder builder) {
    super(builder.wsmResourceFieldsBuilder);
    this.referencedWorkspaceId = builder.referencedWorkspaceId;
    validate();
  }

  public static Builder builder() {
    return new Builder();
  }

  public UUID getReferencedWorkspaceId() {
    return referencedWorkspaceId;
  }

  public ApiTerraWorkspaceAttributes toApiAttributes() {
    return new ApiTerraWorkspaceAttributes().referencedWorkspaceId(referencedWorkspaceId);
  }

  public ApiTerraWorkspaceResource toApiResource() {
    return new ApiTerraWorkspaceResource()
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
    return WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.TERRA_WORKSPACE;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(new ReferencedTerraWorkspaceAttributes(referencedWorkspaceId));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    return new ApiResourceAttributesUnion().terraWorkspace(toApiAttributes());
  }

  @Override
  public ApiResourceUnion toApiResourceUnion() {
    return new ApiResourceUnion().terraWorkspace(toApiResource());
  }

  @Override
  public void validate() {
    super.validate();
    if (referencedWorkspaceId == null) {
      throw new MissingRequiredFieldException(
          "Missing referencedWorkspaceId for ReferencedTerraWorkspaceAttributes.");
    }
  }

  @Override
  public boolean checkAccess(FlightBeanBag context, AuthenticatedUserRequest userRequest) {
    SamService samService = context.getSamService();
    try {
      return samService.isAuthorized(
          userRequest,
          SamConstants.SamResource.WORKSPACE,
          referencedWorkspaceId.toString(),
          SamConstants.SamWorkspaceAction.READ);
    } catch (InterruptedException e) {
      throw new InvalidReferenceException(
          "Requester does not have read access to workspace " + referencedWorkspaceId.toString(),
          e);
    }
  }

  /**
   * Build a builder with values from this object. This is useful when creating related objects that
   * share several values.
   *
   * @return - a Builder for a new ReferencedTerraWorkspaceResource
   */
  public Builder toBuilder() {
    return builder()
        .wsmResourceFieldsBuilder(getWsmResourceFieldsBuilder())
        .referencedWorkspaceId(getReferencedWorkspaceId());
  }

  public static class Builder {
    private UUID referencedWorkspaceId;
    private WsmResourceFields.Builder<?> wsmResourceFieldsBuilder;

    public Builder referencedWorkspaceId(UUID referencedWorkspaceId) {
      this.referencedWorkspaceId = referencedWorkspaceId;
      return this;
    }

    public Builder wsmResourceFieldsBuilder(WsmResourceFields.Builder<?> wsmResourceFieldsBuilder) {
      this.wsmResourceFieldsBuilder = wsmResourceFieldsBuilder;
      return this;
    }

    public ReferencedTerraWorkspaceResource build() {
      // On the create path, we can omit the resourceId and have it filled in by the builder.
      return new ReferencedTerraWorkspaceResource(this);
    }
  }
}
