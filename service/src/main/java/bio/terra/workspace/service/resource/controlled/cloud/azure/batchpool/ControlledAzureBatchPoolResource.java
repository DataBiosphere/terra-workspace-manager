package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolAttributes;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.model.BatchPoolUserAssignedManagedIdentity;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.azure.resourcemanager.batch.models.ApplicationPackageReference;
import com.azure.resourcemanager.batch.models.DeploymentConfiguration;
import com.azure.resourcemanager.batch.models.MetadataItem;
import com.azure.resourcemanager.batch.models.NetworkConfiguration;
import com.azure.resourcemanager.batch.models.ScaleSettings;
import com.azure.resourcemanager.batch.models.StartTask;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ControlledAzureBatchPoolResource extends ControlledResource {
  private String id;
  private String vmSize;
  private String displayName;
  private DeploymentConfiguration deploymentConfiguration;
  private List<BatchPoolUserAssignedManagedIdentity> userAssignedIdentities;
  private ScaleSettings scaleSettings;
  private StartTask startTask;
  private List<ApplicationPackageReference> applicationPackages;
  private NetworkConfiguration networkConfiguration;
  private List<MetadataItem> metadata;

  @JsonCreator
  public ControlledAzureBatchPoolResource(
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
      @JsonProperty("resourceLineage") List<ResourceLineageEntry> resourceLineage,
      @JsonProperty("properties") Map<String, String> properties,
      @JsonProperty("createdByEmail") String createdByEmail,
      @JsonProperty("createdDate") OffsetDateTime createdDate,
      @JsonProperty("lastUpdatedByEmail") String lastUpdatedByEmail,
      @JsonProperty("lastUpdatedDate") OffsetDateTime lastUpdatedDate,
      @JsonProperty("region") String region,
      @JsonProperty("id") String id,
      @JsonProperty("vmSize") String vmSize,
      @JsonProperty("displayName") String displayName,
      @JsonProperty("deploymentConfiguration") DeploymentConfiguration deploymentConfiguration,
      @JsonProperty("userAssignedIdentities")
          List<BatchPoolUserAssignedManagedIdentity> userAssignedIdentities,
      @JsonProperty("scaleSettings") ScaleSettings scaleSettings,
      @JsonProperty("startTask") StartTask startTask,
      @JsonProperty("applicationPackages") List<ApplicationPackageReference> applicationPackages,
      @JsonProperty("networkConfiguration") NetworkConfiguration networkConfiguration,
      @JsonProperty("metadata") List<MetadataItem> metadata) {
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

    this.id = id;
    this.vmSize = vmSize;
    this.displayName = displayName;
    this.deploymentConfiguration = deploymentConfiguration;
    this.userAssignedIdentities = userAssignedIdentities;
    this.scaleSettings = scaleSettings;
    this.startTask = startTask;
    this.applicationPackages = applicationPackages;
    this.networkConfiguration = networkConfiguration;
    this.metadata = metadata;
  }

  /*
  // TODO: PF-2512 remove constructor above and enable this constructor
  @JsonCreator
  public ControlledAzureBatchPoolResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("id") String id,
      @JsonProperty("vmSize") String vmSize,
      @JsonProperty("displayName") String displayName,
      @JsonProperty("deploymentConfiguration") DeploymentConfiguration deploymentConfiguration,
      @JsonProperty("userAssignedIdentities")
          List<BatchPoolUserAssignedManagedIdentity> userAssignedIdentities,
      @JsonProperty("scaleSettings") ScaleSettings scaleSettings,
      @JsonProperty("startTask") StartTask startTask,
      @JsonProperty("applicationPackages") List<ApplicationPackageReference> applicationPackages,
      @JsonProperty("networkConfiguration") NetworkConfiguration networkConfiguration,
      @JsonProperty("metadata") List<MetadataItem> metadata) {
    super(resourceFields, controlledResourceFields);
    this.id = id;
    this.vmSize = vmSize;
    this.displayName = displayName;
    this.deploymentConfiguration = deploymentConfiguration;
    this.userAssignedIdentities = userAssignedIdentities;
    this.scaleSettings = scaleSettings;
    this.startTask = startTask;
    this.applicationPackages = applicationPackages;
    this.networkConfiguration = networkConfiguration;
    this.metadata = metadata;
  }
   */

  private ControlledAzureBatchPoolResource(
      ControlledResourceFields common,
      String id,
      String vmSize,
      String displayName,
      DeploymentConfiguration deploymentConfiguration,
      List<BatchPoolUserAssignedManagedIdentity> userAssignedIdentities,
      ScaleSettings scaleSettings,
      StartTask startTask,
      List<ApplicationPackageReference> applicationPackages,
      NetworkConfiguration networkConfiguration,
      List<MetadataItem> metadata) {
    super(common);
    this.id = id;
    this.vmSize = vmSize;
    this.displayName = displayName;
    this.deploymentConfiguration = deploymentConfiguration;
    this.userAssignedIdentities = userAssignedIdentities;
    this.scaleSettings = scaleSettings;
    this.startTask = startTask;
    this.applicationPackages = applicationPackages;
    this.networkConfiguration = networkConfiguration;
    this.metadata = metadata;
    validate();
  }

  // -- getters used in serialization --
  public WsmResourceFields getWsmResourceFields() {
    return super.getWsmResourceFields();
  }

  public WsmControlledResourceFields getWsmControlledResourceFields() {
    return super.getWsmControlledResourceFields();
  }

  public String getId() {
    return id;
  }

  public String getVmSize() {
    return vmSize;
  }

  public String getDisplayName() {
    return displayName;
  }

  public DeploymentConfiguration getDeploymentConfiguration() {
    return deploymentConfiguration;
  }

  public List<BatchPoolUserAssignedManagedIdentity> getUserAssignedIdentities() {
    return userAssignedIdentities;
  }

  public ScaleSettings getScaleSettings() {
    return scaleSettings;
  }

  public StartTask getStartTask() {
    return startTask;
  }

  public List<ApplicationPackageReference> getApplicationPackages() {
    return applicationPackages;
  }

  public NetworkConfiguration getNetworkConfiguration() {
    return networkConfiguration;
  }

  public List<MetadataItem> getMetadata() {
    return metadata;
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
    return WsmResourceType.CONTROLLED_AZURE_BATCH_POOL;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_BATCH_POOL;
  }

  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessCheckAttributes.UniquenessScope.WORKSPACE)
            .addParameter("id", getId()));
  }

  @Override
  public void addCreateSteps(
      CreateControlledResourceFlight flight,
      String petSaEmail,
      AuthenticatedUserRequest userRequest,
      FlightBeanBag flightBeanBag) {

    flight.addStep(
        new VerifyAzureBatchPoolCanBeCreatedStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            userRequest,
            flightBeanBag.getLandingZoneBatchAccountFinder(),
            this),
        RetryRules.cloud());
    flight.addStep(
        new CreateAzureBatchPoolStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        RetryRules.cloud());
  }

  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAzureBatchPoolStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getLandingZoneBatchAccountFinder(),
            this),
        RetryRules.cloud());
  }

  public ApiAzureBatchPoolAttributes toApiAttributes() {
    return new ApiAzureBatchPoolAttributes().id(getId()).vmSize(getVmSize());
  }

  public ApiAzureBatchPoolResource toApiResource() {
    return new ApiAzureBatchPoolResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(new ControlledAzureBatchPoolAttributes(getId(), getVmSize()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureBatchPool(toApiAttributes());
    return union;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T castByEnum(WsmResourceType expectedType) {
    if (getResourceType() != expectedType) {
      throw new BadRequestException(String.format("Resource is not a %s", expectedType));
    }
    return (T) this;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AZURE_BATCH_POOL
        || getResourceFamily() != WsmResourceFamily.AZURE_BATCH_POOL
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled AZURE_BATCH_POOL");
    }
    ResourceValidationUtils.validateAzureBatchPoolId(getId());
    ResourceValidationUtils.validateAzureVmSize(getVmSize());
    ResourceValidationUtils.validateBatchPoolDisplayName(getDisplayName());
    if (deploymentConfiguration != null) {
      if (deploymentConfiguration.virtualMachineConfiguration() != null
          && deploymentConfiguration.cloudServiceConfiguration() != null) {
        throw new InconsistentFieldsException(
            "Virtual machine configuration and cloud service configuration are mutually exclusive.");
      }
    }
    if (getScaleSettings() != null) {
      if (getScaleSettings().fixedScale() != null && getScaleSettings().autoScale() != null) {
        throw new InconsistentFieldsException(
            "Fixed scale settings and auto scale settings are mutually exclusive.");
      }
    }
    if (userAssignedIdentities != null) {
      var inconsistentUamiCount =
          userAssignedIdentities.stream()
              .filter(uami -> uami.name() != null && uami.clientId() != null)
              .count();
      if (inconsistentUamiCount > 0) {
        throw new InconsistentFieldsException(
            "User assigned managed identity name and clientId are mutually exclusive.");
      }
    }
  }

  public static ControlledAzureBatchPoolResource.Builder builder() {
    return new ControlledAzureBatchPoolResource.Builder();
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String id;
    private String vmSize;
    private String displayName;
    private DeploymentConfiguration deploymentConfiguration;
    private List<BatchPoolUserAssignedManagedIdentity> userAssignedIdentities;
    private ScaleSettings scaleSettings;
    private StartTask startTask;
    private List<ApplicationPackageReference> applicationPackages;
    private NetworkConfiguration networkConfiguration;
    private List<MetadataItem> metadata;

    public ControlledAzureBatchPoolResource.Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public ControlledAzureBatchPoolResource.Builder id(String id) {
      this.id = id;
      return this;
    }

    public ControlledAzureBatchPoolResource.Builder vmSize(String vmSize) {
      this.vmSize = vmSize;
      return this;
    }

    public ControlledAzureBatchPoolResource.Builder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public ControlledAzureBatchPoolResource.Builder deploymentConfiguration(
        DeploymentConfiguration deploymentConfiguration) {
      this.deploymentConfiguration = deploymentConfiguration;
      return this;
    }

    public ControlledAzureBatchPoolResource.Builder userAssignedIdentities(
        List<BatchPoolUserAssignedManagedIdentity> userAssignedIdentities) {
      this.userAssignedIdentities = userAssignedIdentities;
      return this;
    }

    public ControlledAzureBatchPoolResource.Builder scaleSettings(ScaleSettings scaleSettings) {
      this.scaleSettings = scaleSettings;
      return this;
    }

    public ControlledAzureBatchPoolResource.Builder startTask(StartTask startTask) {
      this.startTask = startTask;
      return this;
    }

    public ControlledAzureBatchPoolResource.Builder applicationPackages(
        List<ApplicationPackageReference> applicationPackages) {
      this.applicationPackages = applicationPackages;
      return this;
    }

    public ControlledAzureBatchPoolResource.Builder networkConfiguration(
        NetworkConfiguration networkConfiguration) {
      this.networkConfiguration = networkConfiguration;
      return this;
    }

    public ControlledAzureBatchPoolResource.Builder metadata(List<MetadataItem> metadata) {
      this.metadata = metadata;
      return this;
    }

    public ControlledAzureBatchPoolResource build() {
      return new ControlledAzureBatchPoolResource(
          common,
          id,
          vmSize,
          displayName,
          deploymentConfiguration,
          userAssignedIdentities,
          scaleSettings,
          startTask,
          applicationPackages,
          networkConfiguration,
          metadata);
    }
  }
}
