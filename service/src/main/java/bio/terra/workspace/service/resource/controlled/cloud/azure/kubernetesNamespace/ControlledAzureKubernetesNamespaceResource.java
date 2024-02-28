package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.Step;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiAzureKubernetesNamespaceAttributes;
import bio.terra.workspace.generated.model.ApiAzureKubernetesNamespaceResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.resource.AzureResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.*;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.create.GetAzureCloudContextStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.StepRetryRulePair;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import java.util.*;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link ControlledResource} representing a Kubernetes namespace in an Azure Kubernetes Service.
 * In the Terra context, a Kubernetes namespace can be thought of analogous to a virtual machine.
 * It's a unit of compute that can be used to run containers with an identity. There are 3 flavors
 * of this resource: 1. A namespace that does not have a managed identity associated with it. This
 * can be used for processes that do not need long-lived or independent access external resources.
 * 2. A namespace that has a managed identity associated with it. The identity can be a workspace
 * manged identity or a pet managed identity. 3. A namespace that requires access to workspace
 * databases. This is a special case of #2 where database a role is created for the namespace's
 * managed identity and that role is granted access to the specified databases.
 */
public class ControlledAzureKubernetesNamespaceResource extends ControlledResource {
  private final String kubernetesNamespace;
  private final String kubernetesServiceAccount;
  private final String managedIdentity;
  private final Set<String> databases;

  @JsonCreator
  public ControlledAzureKubernetesNamespaceResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("kubernetesNamespace") String kubernetesNamespace,
      @JsonProperty("kubernetesServiceAccount") String kubernetesServiceAccount,
      @JsonProperty("managedIdentity") String managedIdentity,
      @JsonProperty("databases") Set<String> databases) {
    super(resourceFields, controlledResourceFields);
    this.kubernetesNamespace = kubernetesNamespace;
    this.kubernetesServiceAccount = kubernetesServiceAccount;
    this.managedIdentity = managedIdentity;
    this.databases = databases;
    validate();
  }

  // Constructor for the builder
  private ControlledAzureKubernetesNamespaceResource(
      ControlledResourceFields common,
      String kubernetesNamespace,
      String kubernetesServiceAccount,
      String managedIdentity,
      Set<String> databases) {
    super(common);
    this.kubernetesNamespace = kubernetesNamespace;
    this.kubernetesServiceAccount = kubernetesServiceAccount;
    this.managedIdentity = managedIdentity;
    this.databases = databases;
    validate();
  }

  @Override
  public WsmResourceFields getWsmResourceFields() {
    return super.getWsmResourceFields();
  }

  @Override
  public WsmControlledResourceFields getWsmControlledResourceFields() {
    return super.getWsmControlledResourceFields();
  }

  public String getKubernetesNamespace() {
    return kubernetesNamespace;
  }

  public String getKubernetesServiceAccount() {
    return kubernetesServiceAccount;
  }

  public String getManagedIdentity() {
    return managedIdentity;
  }

  public Set<String> getDatabases() {
    return databases;
  }

  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("kubernetesNamespace", getKubernetesNamespace()));
  }

  @Override
  public void addCreateSteps(
      CreateControlledResourceFlight flight,
      String petSaEmail,
      AuthenticatedUserRequest userRequest,
      FlightBeanBag flightBeanBag) {
    RetryRule cloudRetry = RetryRules.cloud();

    getCreateSteps(flightBeanBag).forEach(step -> flight.addStep(step, cloudRetry));
  }

  @VisibleForTesting
  List<Step> getCreateSteps(FlightBeanBag flightBeanBag) {
    /*
    Flight plan:
    > ensure the kubernetes namespace does not already exist
    > create the kubernetes namespace

    if a managed identity is provided or this is a private resource:
    > get the correct managed identity (either from workspace or pet)
    > create the federated credentials (both KSA and federated credentials in managed identity)

    if databases are provided:
    > create namespace role with access to appropriate databases,
    ensuring databases exist and access is permitted
     */

    final List<Step> createNamespaceSteps =
        List.of(
            new KubernetesNamespaceGuardStep(
                getWorkspaceId(), flightBeanBag.getKubernetesClientProvider(), this),
            new CreateKubernetesNamespaceStep(
                getWorkspaceId(),
                flightBeanBag.getKubernetesClientProvider(),
                this,
                flightBeanBag.getCrlService()));

    return Stream.of(
            createNamespaceSteps,
            getCreateFederatedCredentialsSteps(flightBeanBag),
            getSetupDatabaseAccessSteps(flightBeanBag))
        .flatMap(List::stream)
        .toList();
  }

  private List<Step> getSetupDatabaseAccessSteps(FlightBeanBag flightBeanBag) {
    if (requiresDatabases()) {
      return List.of(
          new CreateNamespaceRoleStep(
              getWorkspaceId(),
              flightBeanBag.getAzureDatabaseUtilsRunner(),
              this,
              flightBeanBag.getResourceDao()));
    } else {
      return List.of();
    }
  }

  private List<Step> getCreateFederatedCredentialsSteps(FlightBeanBag flightBeanBag) {
    if (requiresFederatedCredentials()) {
      return List.of(
          getGetManagedIdentityStep(flightBeanBag, MissingIdentityBehavior.FAIL_ON_MISSING),
          new GetFederatedIdentityStep(
              getKubernetesNamespace(),
              getKubernetesServiceAccount(),
              flightBeanBag.getAzureConfig(),
              flightBeanBag.getCrlService(),
              flightBeanBag.getKubernetesClientProvider(),
              getWorkspaceId()),
          new CreateFederatedIdentityStep(
              getKubernetesNamespace(),
              flightBeanBag.getAzureConfig(),
              flightBeanBag.getCrlService(),
              flightBeanBag.getKubernetesClientProvider(),
              flightBeanBag.getLandingZoneApiDispatch(),
              flightBeanBag.getSamService(),
              flightBeanBag.getWorkspaceService(),
              getWorkspaceId(),
              getKubernetesServiceAccount()));
    } else {
      return List.of();
    }
  }

  // DeleteControlledResourceStep is just a marker interface indicating the step is used in the
  // delete process
  // there's no reason it can't also be used when creating, until we need delete specific behavior
  @NotNull
  private DeleteControlledResourceStep getGetManagedIdentityStep(
      FlightBeanBag flightBeanBag, MissingIdentityBehavior missingIdentityBehavior) {
    return switch (getAccessScope()) {
      case ACCESS_SCOPE_SHARED ->
          new GetWorkspaceManagedIdentityStep(
              getWorkspaceId(),
              getManagedIdentity(),
              missingIdentityBehavior,
              new ManagedIdentityHelper(
                  flightBeanBag.getResourceDao(),
                  flightBeanBag.getCrlService(),
                  flightBeanBag.getAzureConfig()));

      case ACCESS_SCOPE_PRIVATE ->
          new GetPetManagedIdentityStep(
              flightBeanBag.getAzureConfig(),
              flightBeanBag.getCrlService(),
              flightBeanBag.getSamService(),
              getAssignedUser().orElseThrow());
    };
  }

  private boolean requiresFederatedCredentials() {
    return getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE || getManagedIdentity() != null;
  }

  @Override
  public List<DeleteControlledResourceStep> getDeleteSteps(
      FlightMap inputParams, FlightBeanBag flightBeanBag) {
    /*
    Flight plan:
    > delete the kubernetes namespace and wait for it to be deleted

    if a managed identity is provided or this is a private resource:
    > delete federated credentials

    if databases are provided:
    > delete namespace role
    */
    final List<DeleteControlledResourceStep> deleteKubernetesNamespaceSteps =
        List.of(
            new DeleteKubernetesNamespaceStep(
                getWorkspaceId(), flightBeanBag.getKubernetesClientProvider(), this));

    return Stream.of(
            deleteKubernetesNamespaceSteps,
            getFederatedCredentialsDeleteSteps(flightBeanBag),
            getDatabaseAccessDeleteSteps(flightBeanBag))
        .flatMap(List::stream)
        .toList();
  }

  private List<DeleteControlledResourceStep> getDatabaseAccessDeleteSteps(
      FlightBeanBag flightBeanBag) {
    if (requiresDatabases()) {
      return List.of(
          new DeleteNamespaceRoleStep(
              getWorkspaceId(), flightBeanBag.getAzureDatabaseUtilsRunner(), this));
    } else {
      return List.of();
    }
  }

  private boolean requiresDatabases() {
    return getDatabases() != null && !getDatabases().isEmpty();
  }

  private List<DeleteControlledResourceStep> getFederatedCredentialsDeleteSteps(
      FlightBeanBag flightBeanBag) {
    if (requiresFederatedCredentials()) {
      return List.of(
          getGetManagedIdentityStep(flightBeanBag, MissingIdentityBehavior.ALLOW_MISSING),
          new DeleteFederatedCredentialStep(
              getKubernetesNamespace(),
              flightBeanBag.getAzureConfig(),
              flightBeanBag.getCrlService(),
              MissingIdentityBehavior.ALLOW_MISSING));
    } else {
      return List.of();
    }
  }

  @Override
  public List<StepRetryRulePair> getRemoveNativeAccessSteps(FlightBeanBag flightBeanBag) {
    if (requiresDatabases()) {
      return List.of(
          new StepRetryRulePair(
              new GetAzureCloudContextStep(
                  getWorkspaceId(), flightBeanBag.getAzureCloudContextService()),
              RetryRules.shortDatabase()),
          new StepRetryRulePair(
              new UpdateNamespaceRoleDatabaseAccessStep(
                  getWorkspaceId(),
                  flightBeanBag.getAzureDatabaseUtilsRunner(),
                  this,
                  flightBeanBag.getResourceDao(),
                  UpdateNamespaceRoleDatabaseAccessStepMode.REVOKE),
              RetryRules.cloud()));
    } else {
      return List.of();
    }
  }

  @Override
  public List<StepRetryRulePair> getRestoreNativeAccessSteps(FlightBeanBag flightBeanBag) {
    if (requiresDatabases()) {
      return List.of(
          new StepRetryRulePair(
              new GetAzureCloudContextStep(
                  getWorkspaceId(), flightBeanBag.getAzureCloudContextService()),
              RetryRules.shortDatabase()),
          new StepRetryRulePair(
              new UpdateNamespaceRoleDatabaseAccessStep(
                  getWorkspaceId(),
                  flightBeanBag.getAzureDatabaseUtilsRunner(),
                  this,
                  flightBeanBag.getResourceDao(),
                  UpdateNamespaceRoleDatabaseAccessStepMode.RESTORE),
              RetryRules.cloud()));
    } else {
      return List.of();
    }
  }

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_AZURE_KUBERNETES_NAMESPACE;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AZURE_KUBERNETES_NAMESPACE;
  }

  public ApiAzureKubernetesNamespaceResource toApiResource() {
    return new ApiAzureKubernetesNamespaceResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  private ApiAzureKubernetesNamespaceAttributes toApiAttributes() {
    return new ApiAzureKubernetesNamespaceAttributes()
        .kubernetesNamespace(getKubernetesNamespace())
        .kubernetesServiceAccount(getKubernetesServiceAccount())
        .managedIdentity(getManagedIdentity())
        .databases(getDatabases().stream().toList());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureKubernetesNamespaceAttributes(
            getKubernetesNamespace(),
            getKubernetesServiceAccount(),
            getManagedIdentity(),
            getDatabases()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.azureKubernetesNamespace(toApiAttributes());
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

  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_AZURE_KUBERNETES_NAMESPACE
        || getResourceFamily() != WsmResourceFamily.AZURE_KUBERNETES_NAMESPACE
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled AZURE_KUBERNETES_NAMESPACE");
    }
    if (getKubernetesNamespace() == null) {
      throw new MissingRequiredFieldException(
          "Missing required kubernetesNamespace field for ControlledAzureKubernetesNamespace.");
    }
    if (getDatabases() != null
        && !getDatabases().isEmpty()
        && getAccessScope() == AccessScopeType.ACCESS_SCOPE_SHARED
        && getManagedIdentity() == null) {
      throw new InconsistentFieldsException("Databases require an identity.");
    }
    if (getRegion() == null) {
      throw new MissingRequiredFieldException(
          "Missing required region field for ControlledAzureKubernetesNamespace.");
    }
    AzureResourceValidationUtils.validateAzureKubernetesNamespace(getKubernetesNamespace());
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
    ControlledAzureKubernetesNamespaceResource that =
        (ControlledAzureKubernetesNamespaceResource) o;
    return Objects.equals(this.kubernetesNamespace, that.kubernetesNamespace)
        && Objects.equals(this.kubernetesServiceAccount, that.kubernetesServiceAccount)
        && Objects.equals(this.managedIdentity, that.managedIdentity)
        && Objects.equals(this.databases, that.databases);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.kubernetesNamespace,
        this.kubernetesServiceAccount,
        this.managedIdentity,
        this.databases);
  }

  @Override
  public String getRequiredSamActionForPrivateResource() {
    return SamWorkspaceAction.WRITE;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String kubernetesNamespace;
    private String kubernetesServiceAccount;
    private String managedIdentity;
    private Set<String> databases;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public Builder kubernetesNamespace(String kubernetesNamespace) {
      this.kubernetesNamespace = kubernetesNamespace;
      return this;
    }

    public Builder kubernetesServiceAccount(String kubernetesServiceAccount) {
      this.kubernetesServiceAccount = kubernetesServiceAccount;
      return this;
    }

    public Builder managedIdentity(String managedIdentity) {
      this.managedIdentity = managedIdentity;
      return this;
    }

    public Builder databases(Set<String> databases) {
      this.databases = databases;
      return this;
    }

    public ControlledAzureKubernetesNamespaceResource build() {
      return new ControlledAzureKubernetesNamespaceResource(
          common, kubernetesNamespace, kubernetesServiceAccount, managedIdentity, databases);
    }
  }
}
