package bio.terra.workspace.service.resource.referenced;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGcpGcsObjectAttributes;
import bio.terra.workspace.generated.model.ApiGcpGcsObjectResource;
import bio.terra.workspace.generated.model.ApiGitHubRepoAttributes;
import bio.terra.workspace.generated.model.ApiGitHubRepoResource;
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

public class ReferencedGitHubRepoResource extends ReferencedResource{

  private final @Nullable String sshUrl;
  private final String httpsUrl;

  @JsonCreator
  public ReferencedGitHubRepoResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("httpsUrl") String httpsUrl,
      @JsonProperty("sshUrl") @Nullable String sshUrl
  ) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
    this.httpsUrl = httpsUrl;
    this.sshUrl = sshUrl;
    validate();
  }

  /**
   * Constructor from database metadata
   *
   * @param dbResource database form of resources
   */
  public ReferencedGitHubRepoResource(DbResource dbResource) {
    super(dbResource);
    ReferencedGitHubRepoAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferencedGitHubRepoAttributes.class);
    this.httpsUrl = attributes.getHttpsUrl();
    this.sshUrl = attributes.getSshUrl();
    validate();
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.GITHUB_REPO;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(new ReferencedGitHubRepoAttributes(sshUrl, httpsUrl));
  }

  @Override
  public boolean checkAccess(FlightBeanBag context, AuthenticatedUserRequest userRequest) {
    return true;
  }

  public ApiGitHubRepoAttributes toApiAttributes() {
    return new ApiGitHubRepoAttributes().sshUrl(sshUrl);
  }

  public ApiGitHubRepoResource toApiModel() {
    return new ApiGitHubRepoResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  public String getSshUrl() {
    return sshUrl;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.GITHUB_REPO) {
      throw new InconsistentFieldsException("Expected GITHUB_REPO");
    }
    if (Strings.isNullOrEmpty(httpsUrl)) {
      throw new MissingRequiredFieldException(
          "Missing required field for ReferenceGcsObjectResource.");
    }
    ValidationUtils.validateGitRepoHttpsUrl(httpsUrl);
    ValidationUtils.validateGitRepoOptionalSshUrl(sshUrl);
  }

  /**
   * Make a copy of this object via a new builder. This is convenient for reusing objects with one
   * or two fields changed.
   *
   * @return builder object ready for new values to replace existing ones
   */
  public ReferencedGitHubRepoResource.Builder toBuilder() {
    return builder()
        .sshUrl(getSshUrl())
        .cloningInstructions(getCloningInstructions())
        .description(getDescription())
        .name(getName())
        .resourceId(getResourceId())
        .workspaceId(getWorkspaceId());
  }

  public static ReferencedGitHubRepoResource.Builder builder() {
    return new ReferencedGitHubRepoResource.Builder();
  }

  public static class Builder {

    private CloningInstructions cloningInstructions;
    private @Nullable String sshUrl;
    private String httpsUrl;
    private String description;
    private String name;
    private UUID resourceId;
    private UUID workspaceId;

    public ReferencedGitHubRepoResource.Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public ReferencedGitHubRepoResource.Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public ReferencedGitHubRepoResource.Builder name(String name) {
      this.name = name;
      return this;
    }

    public ReferencedGitHubRepoResource.Builder description(String description) {
      this.description = description;
      return this;
    }

    public ReferencedGitHubRepoResource.Builder cloningInstructions(
        CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
      return this;
    }

    public ReferencedGitHubRepoResource.Builder sshUrl(@Nullable String sshUrl) {
      this.sshUrl = sshUrl;
      return this;
    }

    public ReferencedGitHubRepoResource.Builder httpsUrl(String httpsUrl) {
      this.httpsUrl = httpsUrl;
      return this;
    }

    public ReferencedGitHubRepoResource build() {
      // On the create path, we can omit the resourceId and have it filled in by the builder.
      return new ReferencedGitHubRepoResource(
          workspaceId,
          Optional.ofNullable(resourceId).orElse(UUID.randomUUID()),
          name,
          description,
          cloningInstructions,
          httpsUrl,
          sshUrl);
    }
  }
}
