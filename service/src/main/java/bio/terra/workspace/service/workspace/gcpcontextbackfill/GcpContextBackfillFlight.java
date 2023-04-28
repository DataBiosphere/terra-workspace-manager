package bio.terra.workspace.service.workspace.gcpcontextbackfill;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcpContextBackfillFlight extends Flight {
  private static final Logger logger = LoggerFactory.getLogger(GcpContextBackfillFlight.class);

  public GcpContextBackfillFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    WorkspaceDao workspaceDao = flightBeanBag.getWorkspaceDao();
    SamService samService = flightBeanBag.getSamService();

    AuthenticatedUserRequest userRequest =
        FlightUtils.getRequired(
            inputParameters,
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);

    List<String> backfillIds =
        inputParameters.get(WorkspaceFlightMapKeys.UPDATED_WORKSPACES, new TypeReference<>() {});
    if (backfillIds == null || backfillIds.isEmpty()) {
      throw new RuntimeException("No back-fill workspace ids provided to the flight");
    }

    for (String workspaceId : backfillIds) {
      addStep(new GcpContextBackfillStep(workspaceId, workspaceDao, samService, userRequest));
    }
  }

  public static class GcpContextBackfillStep implements Step {
    private static final Logger logger = LoggerFactory.getLogger(GcpContextBackfillStep.class);
    private final UUID workspaceId;
    private final WorkspaceDao workspaceDao;
    private final AuthenticatedUserRequest userRequest;
    private final SamService samService;

    public GcpContextBackfillStep(
        String workspaceId,
        WorkspaceDao workspaceDao,
        SamService samService,
        AuthenticatedUserRequest userRequest) {
      this.workspaceId = UUID.fromString(workspaceId);
      this.workspaceDao = workspaceDao;
      this.samService = samService;
      this.userRequest = userRequest;
    }

    @Override
    public StepResult doStep(FlightContext flightContext)
        throws InterruptedException, RetryException {
      GcpCloudContext context =
          workspaceDao
              .getCloudContext(workspaceId, CloudPlatform.GCP)
              .map(GcpCloudContext::deserialize)
              .orElse(null);

      // The context may have been deleted between when the flight started and when we got here.
      if (context == null) {
        logger.info("Found no GCP cloud context when back-filling workspace {}", workspaceId);
        return StepResult.getStepResultSuccess();
      }

      // The context may have been back-filled before we got here
      if (context.getSamPolicyOwner().isEmpty()) {
        logger.info("Found GCP cloud context already back-filled for workspace {}", workspaceId);
        context.setSamPolicyOwner(
            samService.getWorkspacePolicy(workspaceId, WsmIamRole.OWNER, userRequest));
        context.setSamPolicyWriter(
            samService.getWorkspacePolicy(workspaceId, WsmIamRole.WRITER, userRequest));
        context.setSamPolicyReader(
            samService.getWorkspacePolicy(workspaceId, WsmIamRole.READER, userRequest));
        context.setSamPolicyApplication(
            samService.getWorkspacePolicy(workspaceId, WsmIamRole.APPLICATION, userRequest));
        workspaceDao.updateCloudContext(workspaceId, CloudPlatform.GCP, context.serialize());
      }
      return StepResult.getStepResultSuccess();
    }

    @Override
    public StepResult undoStep(FlightContext context) throws InterruptedException {
      return StepResult.getStepResultSuccess();
    }
  }
}
