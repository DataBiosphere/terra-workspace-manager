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
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.flight.UpdateResourceFlight;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.lang3.builder.EqualsBuilder;

public class ReferencedGitRepoResource extends ReferencedResource {

  private final String gitRepoUrl;

  @JsonCreator
  public ReferencedGitRepoResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("gitRepoUrl") String gitRepoUrl) {
    super(resourceFields);
    this.gitRepoUrl = gitRepoUrl;
    validate();
  }

  private ReferencedGitRepoResource(Builder builder) {
    super(builder.wsmResourceFields);
    this.gitRepoUrl = builder.gitRepoUrl;
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

  // -- getters used in serialization --
  @Override
  public WsmResourceFields getWsmResourceFields() {
    return super.getWsmResourceFields();
  }

  public String getGitRepoUrl() {
    return gitRepoUrl;
  }

  // -- getters not included in serialization --
  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.REFERENCED_ANY_GIT_REPO;
  }

  @Override
  @JsonIgnore
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
  public boolean checkAccess(FlightBeanBag context, AuthenticatedUserRequest userRequest) {
    // WSM doesn't yet store user credential, in this case private auth token or private SSH key.
    // Given it is useful to clone a git repo reference when cloning a workspace even though the
    // credential shouldn't be cloned, we simply skip the access check for now.
    return true;
  }

  @Override
  public WsmResource buildReferencedClone(
      UUID destinationWorkspaceUuid,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      @Nullable String name,
      @Nullable String description,
      String createdByEmail) {
    ReferencedGitRepoResource.Builder resultBuilder =
        toBuilder()
            .wsmResourceFields(
                buildReferencedCloneResourceCommonFields(
                    destinationWorkspaceUuid,
                    destinationResourceId,
                    destinationFolderId,
                    name,
                    description,
                    createdByEmail));
    return resultBuilder.build();
  }

  public ApiGitRepoAttributes toApiAttributes() {
    return new ApiGitRepoAttributes().gitRepoUrl(gitRepoUrl);
  }

  public ApiGitRepoResource toApiResource() {
    return new ApiGitRepoResource().metadata(super.toApiMetadata()).attributes(toApiAttributes());
  }

  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(new UpdateGitRepoReferenceStep());
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

  @Override
  public boolean partialEqual(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ReferencedGitRepoResource that = (ReferencedGitRepoResource) o;

    return new EqualsBuilder()
        .appendSuper(super.partialEqual(o))
        .append(gitRepoUrl, that.getGitRepoUrl())
        .isEquals();
  }

  /**
   * Make a copy of this object via a new builder. This is convenient for reusing objects with one
   * or two fields changed.
   *
   * @return builder object ready for new values to replace existing ones
   */
  public ReferencedGitRepoResource.Builder toBuilder() {
    return builder().gitRepoUrl(getGitRepoUrl()).wsmResourceFields(getWsmResourceFields());
  }

  public static ReferencedGitRepoResource.Builder builder() {
    return new ReferencedGitRepoResource.Builder();
  }

  public static class Builder {

    private WsmResourceFields wsmResourceFields;
    private String gitRepoUrl;

    public ReferencedGitRepoResource.Builder wsmResourceFields(WsmResourceFields resourceFields) {
      this.wsmResourceFields = resourceFields;
      return this;
    }

    public ReferencedGitRepoResource.Builder gitRepoUrl(String gitRepoUrl) {
      this.gitRepoUrl = gitRepoUrl;
      return this;
    }

    public ReferencedGitRepoResource build() {
      return new ReferencedGitRepoResource(this);
    }
  }
}
