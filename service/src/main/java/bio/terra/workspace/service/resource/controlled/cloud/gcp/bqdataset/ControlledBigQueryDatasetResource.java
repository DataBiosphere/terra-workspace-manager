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
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jetbrains.annotations.Nullable;

public class ControlledBigQueryDatasetResource extends ControlledResource {
  private final String datasetName;
  private final String projectId;
  private final Long defaultTableLifetime;
  private final Long defaultPartitionLifetime;

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
      @JsonProperty("properties") Map<String, String> properties,
      @JsonProperty("createdByEmail") String createdByEmail,
      @JsonProperty("createdDate") OffsetDateTime createdDate,
      @JsonProperty("lastUpdatedByEmail") String lastUpdatedByEmail,
      @JsonProperty("lastUpdatedDate") OffsetDateTime lastUpdatedDate,
      @JsonProperty("region") String region,
      @JsonProperty("defaultTableLifetime") Long defaultTableLifetime,
      @JsonProperty("defaultPartitionLifetime") Long defaultPartitionLifetime) {
    super(
        ControlledResourceFields.builder()
            .workspaceUuid(workspaceId)
            .resourceId(resourceId)
            .name(name)
            .description(description)
            .cloningInstructions(cloningInstructions)
            .assignedUser(assignedUser)
            .accessScope(accessScope)
            .managedBy(managedBy)
            .applicationId(applicationId)
            .privateResourceState(privateResourceState)
            .resourceLineage(resourceLineage)
            .properties(properties)
            .createdByEmail(createdByEmail)
            .createdDate(createdDate)
            .lastUpdatedByEmail(lastUpdatedByEmail)
            .lastUpdatedDate(lastUpdatedDate)
            .region(region)
            .build());
    this.datasetName = datasetName;
    this.projectId = projectId;
    this.defaultTableLifetime = defaultTableLifetime;
    this.defaultPartitionLifetime = defaultPartitionLifetime;
    validate();
  }

  /*
    // TODO: PF-2512 remove constructor above and enable this constructor

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

   */

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
  public WsmResourceFields getWsmResourceFields() {
    return super.getWsmResourceFields();
  }

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

  // -- getters for backward compatibility --
  // TODO: PF-2512 Remove these getters
  public UUID getWorkspaceId() {
    return super.getWorkspaceId();
  }

  public UUID getResourceId() {
    return super.getResourceId();
  }

  public String getName() {
    return super.getName();
  }

  public String getDescription() {
    return super.getDescription();
  }

  public CloningInstructions getCloningInstructions() {
    return super.getCloningInstructions();
  }

  public Optional<String> getAssignedUser() {
    return super.getAssignedUser();
  }

  public Optional<PrivateResourceState> getPrivateResourceState() {
    return super.getPrivateResourceState();
  }

  public AccessScopeType getAccessScope() {
    return super.getAccessScope();
  }

  public ManagedByType getManagedBy() {
    return super.getManagedBy();
  }

  public String getApplicationId() {
    return super.getApplicationId();
  }

  public List<ResourceLineageEntry> getResourceLineage() {
    return super.getResourceLineage();
  }

  public ImmutableMap<String, String> getProperties() {
    return super.getProperties();
  }

  public String getCreatedByEmail() {
    return super.getCreatedByEmail();
  }

  public OffsetDateTime getCreatedDate() {
    return super.getCreatedDate();
  }

  public String getLastUpdatedByEmail() {
    return super.getLastUpdatedByEmail();
  }

  public OffsetDateTime getLastUpdatedDate() {
    return super.getLastUpdatedDate();
  }

  public String getRegion() {
    return super.getRegion();
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

  @Override
  public void addUpdateSteps(UpdateControlledResourceFlight flight, FlightBeanBag flightBeanBag) {
    final RetryRule gcpRetryRule = RetryRules.cloud();
    ControlledBigQueryDatasetResource resource =
        getResourceFromFlightInputParameters(
            flight, WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    // retrieve existing attributes in case of undo later
    flight.addStep(
        new RetrieveBigQueryDatasetCloudAttributesStep(
            resource.castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET),
            flightBeanBag.getCrlService(),
            flightBeanBag.getGcpCloudContextService()),
        gcpRetryRule);

    // Update the dataset's cloud attributes
    flight.addStep(
        new UpdateBigQueryDatasetStep(
            resource.castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET),
            flightBeanBag.getCrlService(),
            flightBeanBag.getGcpCloudContextService()),
        gcpRetryRule);
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
