package bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_GCE_INSTANCE_ZONE;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;

public class UpdateGceInstanceResourceZoneAttributesStep implements Step {

  private final ControlledGceInstanceResource resource;
  private final ResourceDao resourceDao;

  public UpdateGceInstanceResourceZoneAttributesStep(
      ControlledGceInstanceResource resource, ResourceDao resourceDao) {
    this.resource = resource;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String previousAttributes = resource.attributesToJson();
    flightContext.getWorkingMap().put(ResourceKeys.PREVIOUS_ATTRIBUTES, previousAttributes);

    String zone =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(), CREATE_GCE_INSTANCE_ZONE, String.class);
    String newAttributes =
        DbSerDes.toJson(
            new ControlledGceInstanceAttributes(
                resource.getInstanceId(), zone, resource.getProjectId()));

    resourceDao.updateResource(
        resource.getWorkspaceId(), resource.getResourceId(), null, null, newAttributes, null);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    String previousAttributes =
        flightContext.getWorkingMap().get(ResourceKeys.PREVIOUS_ATTRIBUTES, String.class);
    resourceDao.updateResource(
        resource.getWorkspaceId(), resource.getResourceId(), null, null, previousAttributes, null);
    return StepResult.getStepResultSuccess();
  }
}
