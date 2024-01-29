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

public class ControlledAzureDatabaseResource extends ControlledResource {
  private final String databaseName;
  private final String databaseOwner;
  private final boolean allowAccessForAllWorkspaceUsers;

  @JsonCreator
  public ControlledAzureDatabaseResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("databaseName") String databaseName,
      @JsonProperty("databaseOwner") String databaseOwner,
      @JsonProperty("allowAccessForAllWorkspaceUsers") boolean allowAccessForAllWorkspaceUsers) {
    super(resourceFields, controlledResourceFields);
    this.databaseName = databaseName;
    this.databaseOwner = databaseOwner;
    this.allowAccessForAllWorkspaceUsers = allowAccessForAllWorkspaceUsers;
    validate();
  }

  // Constructor for the builder
  private ControlledAzureDatabaseResource(
      ControlledResourceFields common,
      String databaseName,
      String databaseOwner,
      boolean allowAccessForAllWorkspaceUsers) {
    super(common);
    this.databaseName = databaseName;
    this.databaseOwner = databaseOwner;
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

  public String getDatabaseOwner() {
    return databaseOwner;
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
        .databaseOwner(getDatabaseOwner())
        .allowAccessForAllWorkspaceUsers(getAllowAccessForAllWorkspaceUsers());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAzureDatabaseAttributes(
            getDatabaseName(), getDatabaseOwner(), getAllowAccessForAllWorkspaceUsers()));
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
        && allowAccessForAllWorkspaceUsers == that.allowAccessForAllWorkspaceUsers;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), databaseName, databaseOwner);
  }

  public static class Builder {
    private ControlledResourceFields common;
    private String databaseName;
    private String databaseOwner;
    private boolean allowAccessForAllWorkspaceUsers;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public Builder databaseName(String databaseName) {
      this.databaseName = databaseName;
      return this;
    }

    public Builder databaseOwner(String databaseOwner) {
      this.databaseOwner = databaseOwner;
      return this;
    }

    public Builder allowAccessForAllWorkspaceUsers(boolean allowAccessForAllWorkspaceUsers) {
      this.allowAccessForAllWorkspaceUsers = allowAccessForAllWorkspaceUsers;
      return this;
    }

    public ControlledAzureDatabaseResource build() {
      return new ControlledAzureDatabaseResource(
          common, databaseName, databaseOwner, allowAccessForAllWorkspaceUsers);
    }
  }
}
