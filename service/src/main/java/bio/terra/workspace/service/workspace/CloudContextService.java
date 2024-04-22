package bio.terra.workspace.service.workspace;

import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.spendprofile.model.SpendProfile;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFlight;
import bio.terra.workspace.service.workspace.model.CloudContext;
import java.util.List;
import java.util.UUID;

/** Interface to define common methods for all cloud context services */
public interface CloudContextService {

  /** Add cloud-specific steps to the create cloud context flight */
  void addCreateCloudContextSteps(
      CreateCloudContextFlight flight,
      FlightBeanBag appContext,
      UUID workspaceUuid,
      SpendProfile spendProfile,
      AuthenticatedUserRequest userRequest);

  /** Make a cloud context of the appropriate type given cloud context from the database */
  CloudContext makeCloudContextFromDb(DbCloudContext dbCloudContext);

  /** Add cloud-specific steps to the delete cloud context flight */
  void addDeleteCloudContextSteps(
      DeleteCloudContextFlight flight,
      FlightBeanBag appContext,
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest);

  /**
   * Make an ordered list of the controlled resources to be deleted when deleting the cloud context
   */
  List<ControlledResource> makeOrderedResourceList(UUID workspaceUuid);

  /**
   * Launch a cloud-appropriate delete flight for deleting a resource as part of deleting the cloud
   * context. DO NOT wait on the flight. Submit and return.
   *
   * <p>We cannot autowire the controlled resource service into the cloud context services, because
   * it makes an autowire loop in Spring.
   */
  void launchDeleteResourceFlight(
      ControlledResourceService controlledResourceService,
      UUID workspaceUuid,
      UUID resourceId,
      String flightId,
      AuthenticatedUserRequest userRequest);
}
