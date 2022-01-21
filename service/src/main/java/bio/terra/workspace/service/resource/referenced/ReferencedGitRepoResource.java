package bio.terra.workspace.service.resource.referenced;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGitRepoAttributes;
import bio.terra.workspace.generated.model.ApiGitRepoResource;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

public class ReferencedGitRepoResource extends ReferencedResource{

  private final String gitUrl;

  @JsonCreator
  public ReferencedGitRepoResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("gitUrl") String gitUrl
  ) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
    this.gitUrl = gitUrl;
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
    this.gitUrl = attributes.getGitUrl();
    validate();
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.GIT_REPO;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(new ReferencedGitRepoAttributes(gitUrl));
  }

  @Override
  public boolean checkAccess(FlightBeanBag context, AuthenticatedUserRequest userRequest) {
    return true;
  }

  public ApiGitRepoAttributes toApiAttributes() {
    return new ApiGitRepoAttributes().gitUrl(gitUrl);
  }

  public ApiGitRepoResource toApiModel() {
    return new ApiGitRepoResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  public String getGitUrl() {
    return gitUrl;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.GIT_REPO) {
      throw new InconsistentFieldsException("Expected GIT_REPO");
    }
    if (Strings.isNullOrEmpty(gitUrl)) {
      throw new MissingRequiredFieldException(
          "Missing required field for ReferenceGcsObjectResource.");
    }
    ValidationUtils.validateGitRepoUrl(gitUrl);
  }

  /**
   * Make a copy of this object via a new builder. This is convenient for reusing objects with one
   * or two fields changed.
   *
   * @return builder object ready for new values to replace existing ones
   */
  public ReferencedGitRepoResource.Builder toBuilder() {
    return builder()
        .gitUrl(getGitUrl())
        .cloningInstructions(getCloningInstructions())
        .description(getDescription())
        .name(getName())
        .resourceId(getResourceId())
        .workspaceId(getWorkspaceId());
  }

  public static ReferencedGitRepoResource.Builder builder() {
    return new ReferencedGitRepoResource.Builder();
  }

  public static class Builder {

    private CloningInstructions cloningInstructions;
    private String gitUrl;
    private String description;
    private String name;
    private UUID resourceId;
    private UUID workspaceId;

    public ReferencedGitRepoResource.Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
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

    public ReferencedGitRepoResource.Builder gitUrl(String gitUrl) {
      this.gitUrl = gitUrl;
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
          gitUrl);
    }
  }
}
