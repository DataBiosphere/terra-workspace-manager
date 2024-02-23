package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.GcpResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.flight.UpdateResourceFlight;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.jetbrains.annotations.Nullable;

public class ControlledBigQueryDatasetResource extends ControlledResource {
  private final String datasetName;
  private final String projectId;
  private final Long defaultTableLifetime;
  private final Long defaultPartitionLifetime;

  @JsonCreator
  public ControlledBigQueryDatasetResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("datasetName") String datasetName,
      @JsonProperty("defaultTableLifetime") Long defaultTableLifetime,
      @JsonProperty("defaultPartitionLifetime") Long defaultPartitionLifetime,
      @JsonProperty("projectId") String projectId) {
    super(resourceFields, controlledResourceFields);
    this.datasetName = datasetName;
    this.defaultTableLifetime = defaultTableLifetime;
    this.defaultPartitionLifetime = defaultPartitionLifetime;
    this.projectId = projectId;
    validate();
  }

  // Constructor for the builder
  private ControlledBigQueryDatasetResource(
      ControlledResourceFields common,
      String datasetName,
      String projectId,
      Long defaultTableLifetime,
      Long defaultPartitionLifetime) {
    super(common);
    this.datasetName = datasetName;
    this.projectId = projectId;
    this.defaultTableLifetime = defaultTableLifetime;
    this.defaultPartitionLifetime = defaultPartitionLifetime;
    validate();
  }

  public static ControlledBigQueryDatasetResource.Builder builder() {
    return new ControlledBigQueryDatasetResource.Builder();
  }

  // -- getters used in serialization --
  @Override
  public WsmResourceFields getWsmResourceFields() {
    return super.getWsmResourceFields();
  }

  @Override
  public WsmControlledResourceFields getWsmControlledResourceFields() {
    return super.getWsmControlledResourceFields();
  }

  public String getDatasetName() {
    return datasetName;
  }

  public String getProjectId() {
    return projectId;
  }

  public Long getDefaultTableLifetime() {
    return defaultTableLifetime;
  }

  public Long getDefaultPartitionLifetime() {
    return defaultPartitionLifetime;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.BIG_QUERY_DATASET;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
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
            userRequest),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public List<DeleteControlledResourceStep> getDeleteSteps(FlightMap inputParameters, FlightBeanBag flightBeanBag) {
    return List.of(new DeleteBigQueryDatasetStep(this, flightBeanBag.getCrlService()));
  }

  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new UpdateBigQueryDatasetStep(flightBeanBag.getCrlService()), RetryRules.cloud());
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
  public WsmResource buildReferencedClone(
      UUID destinationWorkspaceUuid,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      @Nullable String name,
      @Nullable String description,
      String createdByEmail) {
    WsmResourceFields wsmResourceFields =
        buildReferencedCloneResourceCommonFields(
            destinationWorkspaceUuid,
            destinationResourceId,
            destinationFolderId,
            name,
            description,
            createdByEmail);

    final ReferencedBigQueryDatasetResource.Builder resultBuilder =
        ReferencedBigQueryDatasetResource.builder()
            .wsmResourceFields(wsmResourceFields)
            .projectId(getProjectId())
            .datasetName(getDatasetName());

    return resultBuilder.build();
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledBigQueryDatasetAttributes(
            getDatasetName(),
            getProjectId(),
            getDefaultTableLifetime(),
            getDefaultPartitionLifetime()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    return new ApiResourceAttributesUnion().gcpBqDataset(toApiAttributes());
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
    GcpResourceValidationUtils.validateBqDatasetName(getDatasetName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ControlledBigQueryDatasetResource that)) return false;
    if (!super.equals(o)) return false;
    return Objects.equal(datasetName, that.datasetName)
        && Objects.equal(projectId, that.projectId)
        && Objects.equal(defaultTableLifetime, that.defaultTableLifetime)
        && Objects.equal(defaultPartitionLifetime, that.defaultPartitionLifetime);
  }

  @Override
  public boolean partialEqual(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ControlledBigQueryDatasetResource that = (ControlledBigQueryDatasetResource) o;

    return new EqualsBuilder()
        .appendSuper(super.partialEqual(o))
        .append(datasetName, that.datasetName)
        .append(projectId, that.projectId)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        super.hashCode(), datasetName, projectId, defaultTableLifetime, defaultPartitionLifetime);
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String datasetName;
    private String projectId;
    private Long defaultTableLifetime;
    private Long defaultPartitionLifetime;

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

    public ControlledBigQueryDatasetResource.Builder defaultTableLifetime(
        Long defaultTableLifetime) {
      this.defaultTableLifetime = defaultTableLifetime;
      return this;
    }

    public ControlledBigQueryDatasetResource.Builder defaultPartitionLifetime(
        Long defaultPartitionLifetime) {
      this.defaultPartitionLifetime = defaultPartitionLifetime;
      return this;
    }

    public ControlledBigQueryDatasetResource build() {
      return new ControlledBigQueryDatasetResource(
          common, datasetName, projectId, defaultTableLifetime, defaultPartitionLifetime);
    }
  }
}
