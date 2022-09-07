package bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGitRepoAttributes;
import bio.terra.workspace.generated.model.ApiGitRepoResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceUnion;
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
import javax.annotation.Nullable;

public class ReferencedGitRepoResource extends ReferencedResource {

  private final String gitRepoUrl;

  @JsonCreator
  public ReferencedGitRepoResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("gitRepoUrl") String gitRepoUrl,
      @JsonProperty("resourceLineage") List<ResourceLineageEntry> resourceLineage) {
    super(workspaceId, resourceId, name, description, cloningInstructions, resourceLineage);
    this.gitRepoUrl = gitRepoUrl;
    validate();
  }

  /**
   * Constructor from database metadata
   *
   * @param dbResource database form of resources
   */
  public ReferencedGitRepoResource(DbResource dbResource) {
    super(dbResource);
    ReferencedGitRepoAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferencedGitRepoAttributes.class);
    this.gitRepoUrl = attributes.getGitRepoUrl();
    validate();
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
    return WsmResourceType.REFERENCED_ANY_GIT_REPO;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.GIT_REPO;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(new ReferencedGitRepoAttributes(gitRepoUrl));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    return new ApiResourceAttributesUnion().gitRepo(toApiAttributes());
  }

  @Override
  public ApiResourceUnion toApiResourceUnion() {
    return new ApiResourceUnion().gitRepo(toApiResource());
  }

  @Override
  public boolean checkAccess(FlightBeanBag context, AuthenticatedUserRequest userRequest) {
    // WSM doesn't yet store user credential, in this case private auth token or private SSH key.
    // Given it is useful to clone a git repo reference when cloning a workspace even though the
    // credential shouldn't be cloned, we simply skip the access check for now.
    return true;
  }

  public ApiGitRepoAttributes toApiAttributes() {
    return new ApiGitRepoAttributes().gitRepoUrl(gitRepoUrl);
  }

  public ApiGitRepoResource toApiResource() {
    return new ApiGitRepoResource().metadata(super.toApiMetadata()).attributes(toApiAttributes());
  }

  public String getGitRepoUrl() {
    return gitRepoUrl;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.REFERENCED_ANY_GIT_REPO) {
      throw new InconsistentFieldsException("Expected REFERENCED_GIT_REPO");
    }
    if (Strings.isNullOrEmpty(gitRepoUrl)) {
      throw new MissingRequiredFieldException(
          "Missing required field for ReferenceGcsObjectResource.");
    }
  }

  /**
   * Make a copy of this object via a new builder. This is convenient for reusing objects with one
   * or two fields changed.
   *
   * @return builder object ready for new values to replace existing ones
   */
  public ReferencedGitRepoResource.Builder toBuilder() {
    return builder()
        .gitRepoUrl(getGitRepoUrl())
        .cloningInstructions(getCloningInstructions())
        .description(getDescription())
        .name(getName())
        .resourceId(getResourceId())
        .workspaceId(getWorkspaceId())
        .resourceLineage(getResourceLineage());
  }

  public static ReferencedGitRepoResource.Builder builder() {
    return new ReferencedGitRepoResource.Builder();
  }

  public static class Builder {

    private CloningInstructions cloningInstructions;
    private String gitRepoUrl;
    private String description;
    private String name;
    private UUID resourceId;
    private UUID workspaceId;
    private List<ResourceLineageEntry> resourceLineage;

    public ReferencedGitRepoResource.Builder workspaceId(UUID workspaceUuid) {
      this.workspaceId = workspaceUuid;
      return this;
    }

    public ReferencedGitRepoResource.Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public ReferencedGitRepoResource.Builder name(String name) {
      this.name = name;
      return this;
    }

    public ReferencedGitRepoResource.Builder description(String description) {
      this.description = description;
      return this;
    }

    public ReferencedGitRepoResource.Builder cloningInstructions(
        CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
      return this;
    }

    public ReferencedGitRepoResource.Builder gitRepoUrl(String gitRepoUrl) {
      this.gitRepoUrl = gitRepoUrl;
      return this;
    }

    public Builder resourceLineage(List<ResourceLineageEntry> resourceLineage) {
      this.resourceLineage = resourceLineage;
      return this;
    }

    public ReferencedGitRepoResource build() {
      // On the create path, we can omit the resourceId and have it filled in by the builder.
      return new ReferencedGitRepoResource(
          workspaceId,
          Optional.ofNullable(resourceId).orElse(UUID.randomUUID()),
          name,
          description,
          cloningInstructions,
          gitRepoUrl,
          resourceLineage);
    }
  }
}
