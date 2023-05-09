package bio.terra.workspace.service.workspace;

import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFlight;
import bio.terra.workspace.service.workspace.model.CloudContext;
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
}
