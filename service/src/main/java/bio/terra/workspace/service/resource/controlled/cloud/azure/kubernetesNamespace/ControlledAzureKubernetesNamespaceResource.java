package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleNone;
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
import bio.terra.workspace.service.resource.AzureResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.CreateFederatedIdentityStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.DeleteFederatedCredentialStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetFederatedIdentityStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetPetManagedIdentityStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetWorkspaceManagedIdentityStep;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class ControlledAzureKubernetesNamespaceResource extends ControlledResource {
  private final String kubernetesNamespace;
  private final String kubernetesServiceAccount;
  private final UUID managedIdentity;
  private final Set<UUID> databases;

  @JsonCreator
  public ControlledAzureKubernetesNamespaceResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("kubernetesNamespace") String kubernetesNamespace,
      @JsonProperty("kubernetesServiceAccount") String kubernetesServiceAccount,
      @JsonProperty("managedIdentity") UUID managedIdentity,
      @JsonProperty("databases") Set<UUID> databases) {
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
      UUID managedIdentity,
      Set<UUID> databases) {
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

  public UUID getManagedIdentity() {
    return managedIdentity;
  }

  public Set<UUID> getDatabases() {
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
    RetryRule cloudRetry = new RetryRuleNone();

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
    > create database login role with access to appropriate databases, ensuring databases exist
     */

    final List<Step> createNamespaceSteps =
        List.of(
            new KubernetesNamespaceGuardStep(
                getWorkspaceId(), flightBeanBag.getKubernetesClientProvider(), this),
            new CreateKubernetesNamespaceStep(
                getWorkspaceId(), flightBeanBag.getKubernetesClientProvider(), this));

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
          new CreateDatabaseUserStep(
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
          getGetManagedIdentityStep(flightBeanBag),
          new GetFederatedIdentityStep(
              getKubernetesNamespace(),
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

  @NotNull
  private Step getGetManagedIdentityStep(FlightBeanBag flightBeanBag) {
    return switch (getAccessScope()) {
      case ACCESS_SCOPE_SHARED -> new GetWorkspaceManagedIdentityStep(
          flightBeanBag.getAzureConfig(),
          flightBeanBag.getCrlService(),
          getWorkspaceId(),
          flightBeanBag.getResourceDao(),
          getManagedIdentity());

      case ACCESS_SCOPE_PRIVATE -> new GetPetManagedIdentityStep(
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
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    RetryRule cloudRetry = RetryRules.cloud();

    getDeleteSteps(flightBeanBag).forEach(step -> flight.addStep(step, cloudRetry));
  }

  @VisibleForTesting
  List<Step> getDeleteSteps(FlightBeanBag flightBeanBag) {
    /*
    Flight plan:
    > delete the kubernetes namespace and wait for it to be deleted

    if a managed identity is provided or this is a private resource:
    > delete federated credentials

    if databases are provided:
    > delete database login role
    */
    final List<Step> deleteKubernetesNamespaceSteps =
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

  private List<Step> getDatabaseAccessDeleteSteps(FlightBeanBag flightBeanBag) {
    if (requiresDatabases()) {
      return List.of(
          new DeleteDatabaseUserStep(
              getWorkspaceId(), flightBeanBag.getAzureDatabaseUtilsRunner(), this));
    } else {
      return List.of();
    }
  }

  private boolean requiresDatabases() {
    return getDatabases() != null && !getDatabases().isEmpty();
  }

  private List<Step> getFederatedCredentialsDeleteSteps(FlightBeanBag flightBeanBag) {
    if (requiresFederatedCredentials()) {
      return List.of(
          getGetManagedIdentityStep(flightBeanBag),
          new GetFederatedIdentityStep(
              getKubernetesNamespace(),
              flightBeanBag.getAzureConfig(),
              flightBeanBag.getCrlService(),
              flightBeanBag.getKubernetesClientProvider(),
              getWorkspaceId()),
          new DeleteFederatedCredentialStep(
              getKubernetesNamespace(),
              flightBeanBag.getAzureConfig(),
              flightBeanBag.getCrlService()));
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

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String kubernetesNamespace;
    private String kubernetesServiceAccount;
    private UUID managedIdentity;
    private Set<UUID> databases;

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

    public Builder managedIdentity(UUID managedIdentity) {
      this.managedIdentity = managedIdentity;
      return this;
    }

    public Builder databases(Set<UUID> databases) {
      this.databases = databases;
      return this;
    }

    public ControlledAzureKubernetesNamespaceResource build() {
      return new ControlledAzureKubernetesNamespaceResource(
          common, kubernetesNamespace, kubernetesServiceAccount, managedIdentity, databases);
    }
  }
}
