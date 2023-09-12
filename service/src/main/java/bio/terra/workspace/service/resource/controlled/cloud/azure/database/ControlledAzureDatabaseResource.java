package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.Step;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureDatabaseAttributes;
import bio.terra.workspace.generated.model.ApiAzureDatabaseResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.AzureResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.CreateFederatedIdentityStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetFederatedIdentityStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetPetManagedIdentityStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetWorkspaceManagedIdentityStep;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
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
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ControlledAzureDatabaseResource extends ControlledResource {
  private final String databaseName;
  private final UUID databaseOwner;
  private final String k8sNamespace;
  private final boolean allowAccessForAllWorkspaceUsers;

  @JsonCreator
  public ControlledAzureDatabaseResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("databaseName") String databaseName,
      @JsonProperty("databaseOwner") UUID databaseOwner,
      @JsonProperty("k8sNamespace") String k8sNamespace,
      @JsonProperty("allowAccessForAllWorkspaceUsers") boolean allowAccessForAllWorkspaceUsers) {
    super(resourceFields, controlledResourceFields);
    this.databaseName = databaseName;
    this.databaseOwner = databaseOwner;
    this.k8sNamespace = k8sNamespace;
    this.allowAccessForAllWorkspaceUsers = allowAccessForAllWorkspaceUsers;
    validate();
  }

  // Constructor for the builder
  private ControlledAzureDatabaseResource(
      ControlledResourceFields common,
      String databaseName,
      UUID databaseOwner,
      String k8sNamespace,
      boolean allowAccessForAllWorkspaceUsers) {
    super(common);
    this.databaseName = databaseName;
    this.databaseOwner = databaseOwner;
    this.k8sNamespace = k8sNamespace;
    this.allowAccessForAllWorkspaceUsers = allowAccessForAllWorkspaceUsers;
    validate();
  }

  public static Builder builder() {
    return new Builder();
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

  public boolean getAllowAccessForAllWorkspaceUsers() {
    return allowAccessForAllWorkspaceUsers;
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

    getAddSteps(flightBeanBag).forEach(step -> flight.addStep(step, cloudRetry));
  }

  @VisibleForTesting
  List<Step> getAddSteps(FlightBeanBag flightBeanBag) {
    ArrayList<Step> steps = new ArrayList<>();
    steps.add(new ValidateDatabaseOwnerStep(this, flightBeanBag.getResourceDao()));
    steps.add(
        new AzureDatabaseGuardStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            this,
            flightBeanBag.getSamService(),
            flightBeanBag.getLandingZoneApiDispatch(),
            flightBeanBag.getWorkspaceService(),
            getWorkspaceId()));
    maybeSetupFederatedIdentity(flightBeanBag, steps);
    steps.add(
        new CreateAzureDatabaseStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            this,
            flightBeanBag.getLandingZoneApiDispatch(),
            flightBeanBag.getSamService(),
            flightBeanBag.getWorkspaceService(),
            getWorkspaceId(),
            flightBeanBag.getAzureDatabaseUtilsRunner()));
    return steps;
  }

  /**
   * The first iteration of creating azure databases setup federated identities when setting up the
   * database. The absence of k8sNamespace indicates that this is a subsequent iteration where a
   * KubernetesNamespace controlled resource takes care of that.
   *
   * @param flightBeanBag flightBeanBag
   * @param steps steps
   */
  private void maybeSetupFederatedIdentity(FlightBeanBag flightBeanBag, ArrayList<Step> steps) {
    // TODO: remove as part of https://broadworkbench.atlassian.net/browse/WOR-1165
    if (k8sNamespace != null) {
      Step getManagedIdentityStep =
          switch (getAccessScope()) {
            case ACCESS_SCOPE_SHARED -> new GetWorkspaceManagedIdentityStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag.getCrlService(),
                getWorkspaceId(),
                flightBeanBag.getResourceDao(),
                getDatabaseOwner());

            case ACCESS_SCOPE_PRIVATE -> new GetPetManagedIdentityStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getSamService(),
                getAssignedUser().orElseThrow());
          };
      steps.add(getManagedIdentityStep);
      steps.add(
          new GetFederatedIdentityStep(
              getK8sNamespace(),
              flightBeanBag.getAzureConfig(),
              flightBeanBag.getCrlService(),
              flightBeanBag.getKubernetesClientProvider(),
              getWorkspaceId()));
      steps.add(
          new CreateFederatedIdentityStep(
              getK8sNamespace(),
              flightBeanBag.getAzureConfig(),
              flightBeanBag.getCrlService(),
              flightBeanBag.getKubernetesClientProvider(),
              flightBeanBag.getLandingZoneApiDispatch(),
              flightBeanBag.getSamService(),
              flightBeanBag.getWorkspaceService(),
              getWorkspaceId(),
              null));
    }
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
    return new ApiAzureDatabaseAttributes()
        .databaseName(getDatabaseName())
        .databaseOwner(Objects.toString(getDatabaseOwner(), null))
        .allowAccessForAllWorkspaceUsers(getAllowAccessForAllWorkspaceUsers());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureDatabaseAttributes(
            getDatabaseName(),
            getDatabaseOwner(),
            getK8sNamespace(),
            getAllowAccessForAllWorkspaceUsers()));
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
    if (getAccessScope() == AccessScopeType.ACCESS_SCOPE_SHARED && getDatabaseOwner() == null) {
      throw new MissingRequiredFieldException(
          "Missing required databaseOwner field for shared ControlledAzureDatabase.");
    }
    if (getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE
        && getAllowAccessForAllWorkspaceUsers()) {
      throw new InconsistentFieldsException(
          "Private access databases cannot allow access for all workspace users.");
    }
    if (getRegion() == null) {
      throw new MissingRequiredFieldException(
          "Missing required region field for ControlledAzureDatabase.");
    }
    AzureResourceValidationUtils.validateAzureDatabaseName(getDatabaseName());
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o) && partialEqual(o);
  }

  @Override
  public boolean partialEqual(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ControlledAzureDatabaseResource that = (ControlledAzureDatabaseResource) o;
    return Objects.equals(databaseName, that.databaseName)
        && Objects.equals(databaseOwner, that.databaseOwner)
        && Objects.equals(k8sNamespace, that.k8sNamespace)
        && allowAccessForAllWorkspaceUsers == that.allowAccessForAllWorkspaceUsers;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), databaseName, databaseOwner, k8sNamespace);
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String databaseName;
    private UUID databaseOwner;
    private String k8sNamespace;
    private boolean allowAccessForAllWorkspaceUsers;

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

    public Builder allowAccessForAllWorkspaceUsers(boolean allowAccessForAllWorkspaceUsers) {
      this.allowAccessForAllWorkspaceUsers = allowAccessForAllWorkspaceUsers;
      return this;
    }

    public ControlledAzureDatabaseResource build() {
      return new ControlledAzureDatabaseResource(
          common, databaseName, databaseOwner, k8sNamespace, allowAccessForAllWorkspaceUsers);
    }
  }
}
