package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class ControlledBigQueryDatasetResource extends ControlledResource {
  private final String datasetName;
  private final String projectId;

  @JsonCreator
  public ControlledBigQueryDatasetResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("assignedUser") String assignedUser,
      @JsonProperty("privateResourceState") PrivateResourceState privateResourceState,
      @JsonProperty("accessScope") AccessScopeType accessScope,
      @JsonProperty("managedBy") ManagedByType managedBy,
      @JsonProperty("applicationId") String applicationId,
      @JsonProperty("datasetName") String datasetName,
      @JsonProperty("projectId") String projectId,
      @JsonProperty("resourceLineage") List<ResourceLineageEntry> resourceLineage,
      @JsonProperty("properties") Map<String, String> properties) {
    super(
        workspaceId,
        resourceId,
        name,
        description,
        cloningInstructions,
        assignedUser,
        accessScope,
        managedBy,
        applicationId,
        privateResourceState,
        resourceLineage,
        properties);
    this.datasetName = datasetName;
    this.projectId = projectId;
    validate();
  }

  // Constructor for the builder
  private ControlledBigQueryDatasetResource(
      ControlledResourceFields common, String datasetName, String projectId) {
    super(common);
    this.datasetName = datasetName;
    this.projectId = projectId;
    validate();
  }

  public static ControlledBigQueryDatasetResource.Builder builder() {
    return new ControlledBigQueryDatasetResource.Builder();
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

  /** {@inheritDoc} */
  @Override
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("datasetName", getDatasetName()));
  }

  /** {@inheritDoc} */
  @Override
  public void addCreateSteps(
      CreateControlledResourceFlight flight,
      String petSaEmail,
      AuthenticatedUserRequest userRequest,
      FlightBeanBag flightBeanBag) {
    RetryRule cloudRetry = RetryRules.cloud();
    // Unlike other resources, BigQuery datasets set IAM permissions at creation time to avoid
    // unwanted defaults from GCP.
    flight.addStep(
        new CreateBigQueryDatasetStep(
            flightBeanBag.getControlledResourceService(),
            flightBeanBag.getCrlService(),
            this,
            flightBeanBag.getGcpCloudContextService(),
            userRequest),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteBigQueryDatasetStep(this, flightBeanBag.getCrlService()), RetryRules.cloud());
  }

  public String getDatasetName() {
    return datasetName;
  }

  public String getProjectId() {
    return projectId;
  }

  public ApiGcpBigQueryDatasetAttributes toApiAttributes() {
    return new ApiGcpBigQueryDatasetAttributes()
        .projectId(getProjectId())
        .datasetId(getDatasetName());
  }

  public ApiGcpBigQueryDatasetResource toApiResource() {
    return new ApiGcpBigQueryDatasetResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.BIG_QUERY_DATASET;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledBigQueryDatasetAttributes(getDatasetName(), getProjectId()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    return new ApiResourceAttributesUnion().gcpBqDataset(toApiAttributes());
  }

  @Override
  public ApiResourceUnion toApiResourceUnion() {
    return new ApiResourceUnion().gcpBqDataset(toApiResource());
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET
        || getResourceFamily() != WsmResourceFamily.BIG_QUERY_DATASET
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled GCP BIG_QUERY_DATASET");
    }
    if (getDatasetName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required field datasetName for BigQuery dataset");
    }
    if (getProjectId() == null) {
      throw new MissingRequiredFieldException(
          "Missing required field projectId for BigQuery dataset");
    }
    ResourceValidationUtils.validateBqDatasetName(getDatasetName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ControlledBigQueryDatasetResource that = (ControlledBigQueryDatasetResource) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(datasetName, that.datasetName)
        .append(projectId, that.projectId)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(datasetName)
        .append(projectId)
        .toHashCode();
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String datasetName;
    private String projectId;

    public ControlledBigQueryDatasetResource.Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public ControlledBigQueryDatasetResource.Builder datasetName(String datasetName) {
      this.datasetName = datasetName;
      return this;
    }

    public ControlledBigQueryDatasetResource.Builder projectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    public ControlledBigQueryDatasetResource build() {
      return new ControlledBigQueryDatasetResource(common, datasetName, projectId);
    }
  }
}
