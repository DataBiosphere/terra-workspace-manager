package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureDatabaseAttributes;
import bio.terra.workspace.generated.model.ApiAzureDatabaseResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.cloud.azure.KubernetesClientProviderImpl;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.CreateFederatedIdentityStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetFederatedIdentityStep;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.flight.UpdateResourceFlight;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import java.util.UUID;

public class ControlledAzureDatabaseResource extends ControlledResource {
  private final String databaseName;
  private final UUID databaseOwner;

  private final String k8sNamespace;

  @JsonCreator
  public ControlledAzureDatabaseResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("databaseName") String databaseName,
      @JsonProperty("databaseOwner") UUID databaseOwner,
      @JsonProperty("k8sNamespace") String k8sNamespace) {
    super(resourceFields, controlledResourceFields);
    this.databaseName = databaseName;
    this.databaseOwner = databaseOwner;
    this.k8sNamespace = k8sNamespace;
    validate();
  }

  // Constructor for the builder
  private ControlledAzureDatabaseResource(
      ControlledResourceFields common,
      String databaseName,
      UUID databaseOwner,
      String k8sNamespace) {
    super(common);
    this.databaseName = databaseName;
    this.databaseOwner = databaseOwner;
    this.k8sNamespace = k8sNamespace;
    validate();
  }

  public static Builder builder() {
    return new Builder();
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

  @Override
  public WsmControlledResourceFields getWsmControlledResourceFields() {
    return super.getWsmControlledResourceFields();
  }

  public String getDatabaseName() {
    return databaseName;
  }

  public UUID getDatabaseOwner() {
    return databaseOwner;
  }

  public String getK8sNamespace() {
    return k8sNamespace;
  }
  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_DATABASE;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_DATABASE;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("databaseName", getDatabaseName()));
  }

  /** {@inheritDoc} */
  @Override
  public void addCreateSteps(
      CreateControlledResourceFlight flight,
      String petSaEmail,
      AuthenticatedUserRequest userRequest,
      FlightBeanBag flightBeanBag) {
    RetryRule cloudRetry = RetryRules.cloud();

    flight.addStep(
        new GetAzureDatabaseStep(
            flightBeanBag.getAzureConfig(), flightBeanBag.getCrlService(), this),
        cloudRetry);

    flight.addStep(
        new GetFederatedIdentityStep(
            getK8sNamespace(),
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            getDatabaseOwner(),
            new KubernetesClientProviderImpl(),
            flightBeanBag.getLandingZoneApiDispatch(),
            flightBeanBag.getSamService(),
            flightBeanBag.getWorkspaceService(),
            getWorkspaceId(),
            flightBeanBag.getResourceDao()),
        cloudRetry);

    flight.addStep(
        new CreateFederatedIdentityStep(
            getK8sNamespace(),
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            getDatabaseOwner(),
            new KubernetesClientProviderImpl(),
            flightBeanBag.getLandingZoneApiDispatch(),
            flightBeanBag.getSamService(),
            flightBeanBag.getWorkspaceService(),
            getWorkspaceId(),
            flightBeanBag.getResourceDao()),
        cloudRetry);
    flight.addStep(
        new CreateAzureDatabaseStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            this,
            flightBeanBag.getLandingZoneApiDispatch(),
            flightBeanBag.getSamService(),
            flightBeanBag.getWorkspaceService(),
            getWorkspaceId(),
            new KubernetesClientProviderImpl(),
            flightBeanBag.getResourceDao()),
        cloudRetry);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAzureDatabaseStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            this,
            flightBeanBag.getLandingZoneApiDispatch(),
            flightBeanBag.getSamService(),
            flightBeanBag.getWorkspaceService(),
            getWorkspaceId()),
        RetryRules.cloud());
  }

  // Azure resources currently do not implement updating.
  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {}

  public ApiAzureDatabaseResource toApiResource() {
    return new ApiAzureDatabaseResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  private ApiAzureDatabaseAttributes toApiAttributes() {
    return new ApiAzureDatabaseAttributes().databaseName(getDatabaseName());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureDatabaseAttributes(getDatabaseName(), getDatabaseOwner(), k8sNamespace,
            getRegion()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureDatabase(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AZURE_DATABASE
        || getResourceFamily() != WsmResourceFamily.AZURE_DATABASE
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled AZURE_DATABASE");
    }
    if (getDatabaseName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required databaseName field for ControlledAzureDatabase.");
    }
    if (getDatabaseOwner() == null) {
      throw new MissingRequiredFieldException(
          "Missing required databaseOwner field for ControlledAzureDatabase.");
    }
    if (getRegion() == null) {
      throw new MissingRequiredFieldException(
          "Missing required region field for ControlledAzureDatabase.");
    }
    ResourceValidationUtils.validateAzureDatabaseName(getDatabaseName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAzureDatabaseResource that = (ControlledAzureDatabaseResource) o;

    return databaseName.equals(that.getDatabaseName());
  }

  @Override
  public boolean partialEqual(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.partialEqual(o)) return false;

    ControlledAzureDatabaseResource that = (ControlledAzureDatabaseResource) o;

    return databaseName.equals(that.getDatabaseName());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + databaseName.hashCode();
    return result;
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String databaseName;
    private UUID databaseOwner;
    private String k8sNamespace;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public Builder databaseName(String databaseName) {
      this.databaseName = databaseName;
      return this;
    }

    public Builder databaseOwner(UUID databaseOwner) {
      this.databaseOwner = databaseOwner;
      return this;
    }

    public Builder k8sNamespace(String k8sNamespace) {
      this.k8sNamespace = k8sNamespace;
      return this;
    }

    public ControlledAzureDatabaseResource build() {
      return new ControlledAzureDatabaseResource(common, databaseName, databaseOwner, k8sNamespace);
    }
  }
}
