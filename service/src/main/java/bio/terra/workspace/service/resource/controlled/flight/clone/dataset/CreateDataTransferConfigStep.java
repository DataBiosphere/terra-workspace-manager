package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import static bio.terra.workspace.common.utils.GcpUtils.getControlPlaneProjectId;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.gax.rpc.FailedPreconditionException;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import com.google.api.services.iam.v1.model.Binding;
import com.google.api.services.iam.v1.model.Policy;
import com.google.api.services.iam.v1.model.SetIamPolicyRequest;
import com.google.cloud.bigquery.datatransfer.v1.CreateTransferConfigRequest;
import com.google.cloud.bigquery.datatransfer.v1.DataTransferServiceClient;
import com.google.cloud.bigquery.datatransfer.v1.ProjectName;
import com.google.cloud.bigquery.datatransfer.v1.TransferConfig;
import com.google.cloud.bigquery.datatransfer.v1.TransferConfigName;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateDataTransferConfigStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateDataTransferConfigStep.class);
  private static final Duration SCHEDULE_DELAY = Duration.ofMinutes(1);
  private static final String FQ_P4_SA_FORMAT =
      "projects/%s/serviceAccounts/service-%s@gcp-sa-bigquerydatatransfer.iam.gserviceaccount.com";
  // Format specified on the command line for binding members
  private static final String CLI_P4_SA_FORMAT =
      "serviceAccount:service-%s@gcp-sa-bigquerydatatransfer.iam.gserviceaccount.com";

  private final CrlService crlService;

  public CreateDataTransferConfigStep(CrlService crlService) {
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final String controlPlaneProjectId = getControlPlaneProjectId();
    flightContext
        .getWorkingMap()
        .put(ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID, controlPlaneProjectId);

    final DatasetCloneInputs sourceInputs =
        workingMap.get(ControlledResourceKeys.SOURCE_CLONE_INPUTS, DatasetCloneInputs.class);
    final DatasetCloneInputs destinationInputs =
        workingMap.get(ControlledResourceKeys.DESTINATION_CLONE_INPUTS, DatasetCloneInputs.class);
    final String location =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            ControlledResourceKeys.LOCATION,
            ControlledResourceKeys.LOCATION,
            String.class);
    final String controlPlaneSaEmail =
        workingMap.get(ControlledResourceKeys.CONTROL_PLANE_SA_EMAIL, String.class);
    final String controlPlaneSaFqid =
        String.format("projects/%s/serviceAccounts/%s", controlPlaneProjectId, controlPlaneSaEmail);
    final String transferConfigName =
        TransferConfigName.ofProjectLocationTransferConfigName(
                destinationInputs.getProjectId(), location, flightContext.getFlightId())
            .toString();

    try (DataTransferServiceClient dataTransferServiceClient = DataTransferServiceClient.create()) {
      // Call a method on the client to force it it to create the "P4" service account. This must
      // exist so that we can give it a role for it to impersonate the main SA. Ignore the output.
      dataTransferServiceClient.listDataSources(ProjectName.of(controlPlaneProjectId));

      final Map<String, Value> params = new HashMap<>();
      params.put(
          "source_project_id",
          Value.newBuilder().setStringValue(sourceInputs.getProjectId()).build());
      params.put(
          "source_dataset_id",
          Value.newBuilder().setStringValue(sourceInputs.getDatasetName()).build());
      params.put("overwrite_destination_table", Value.newBuilder().setBoolValue(false).build());

      final TransferConfig inputTransferConfig =
          TransferConfig.newBuilder()
              .setDestinationDatasetId(destinationInputs.getDatasetName())
              //              .setName(transferConfigName)
              .setDisplayName("Dataset Clone")
              .setParams(Struct.newBuilder().putAllFields(params).build())
              .setDataSourceId("cross_region_copy")
              //              .setSchedule(buildSchedule())
              .build();
      final CreateTransferConfigRequest createTransferConfigRequest =
          CreateTransferConfigRequest.newBuilder()
              .setParent(ProjectName.of(destinationInputs.getProjectId()).toString())
              .setTransferConfig(inputTransferConfig)
              .build();

      // Run, and fail, once to prime the pump and create the P4 service account.
      try {
        dataTransferServiceClient.createTransferConfig(createTransferConfigRequest);
      } catch (FailedPreconditionException expected) {
        logger.info("Received expected failure: ", expected);
      }

      // The destination project has a P4 service account which needs permission to impersonate the
      // main
      // control plane service account and do the transfer.
      final String destinationP4sa = getP4ServiceAccount(destinationInputs.getProjectId());
      // delegate role to P4 service account
      final Policy updatedPolicy = delegatePolicyBinding(controlPlaneSaFqid, destinationP4sa);
      final TransferConfig createdConfig =
          dataTransferServiceClient.createTransferConfig(createTransferConfigRequest);
      final String dataSourceId = createdConfig.getDataSourceId(); // FIXME
      //      final StartManualTransferRunsRequest manualTransferRunsRequest =
      //
      // StartManualTransferRunsRequest.newBuilder().setParent(createdConfig.getName()).build();
      //
      //      final StartManualTransferRunsResponse response =
      //          dataTransferServiceClient.startManualTransferRuns(manualTransferRunsRequest);
      //      final List<TransferRun> runs = response.getRunsList();
      //      final int runsCount = response.getRunsCount();
    } catch (IOException | RuntimeException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private String getP4ServiceAccount(String destinationProjectId) {
    // get the P4 service account name. First, determine the destination project number
    try {
      final CloudResourceManagerCow cloudResourceManagerCow =
          crlService.getCloudResourceManagerCow();
      final Project project =
          cloudResourceManagerCow.projects().get(destinationProjectId).execute();
      // Project "name" has the form projects/123456. A.k.a. the project number.
      final String projectName = project.getName();
      String[] parts = projectName.split("/");
      final String projectNumber = parts[1];
      //      return String.format(FQ_P4_SA_FORMAT, destinationProjectId, projectNumber);
      return String.format(CLI_P4_SA_FORMAT, projectNumber);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          String.format("Cannot determine project number for project ID %s", destinationProjectId),
          e);
    }
  }

  /**
   * Set a binding to the role roles/iam.serviceAccountTokenCreator to the P4 service account
   * associated with the user's project and the gcp-sa-bigquerydatatransfer service. See go/p4sa.
   *
   * @param controlPlaneSA - controll plane service account in format
   *     projects/projectId/serviceAccounts/serviceAccountEmail
   * @param p4sa - service account in format serviceAccount:serviceAccountEmail
   * @return updated policy
   */
  private Policy delegatePolicyBinding(String controlPlaneSA, String p4sa) throws IOException {
    final IamCow iamCow = crlService.getIamCow();
    final Policy policy =
        iamCow.projects().serviceAccounts().getIamPolicy(controlPlaneSA).execute();
    // Add the binding to the existing policy
    final List<Binding> existingBindings = policy.getBindings();

    final Binding tokenCreatorBinding =
        new Binding()
            .setRole("roles/iam.serviceAccountTokenCreator")
            .setMembers(Collections.singletonList(p4sa));
    final List<Binding> newBindings =
        ImmutableList.<Binding>builder().addAll(existingBindings).add(tokenCreatorBinding).build();
    policy.setBindings(newBindings);
    final SetIamPolicyRequest policyRequest = new SetIamPolicyRequest().setPolicy(policy);
    return iamCow
        .projects()
        .serviceAccounts()
        .setIamPolicy(controlPlaneSA, policyRequest)
        .execute();
  }

  private String buildSchedule() {
    final OffsetDateTime scheduledTime = OffsetDateTime.now(ZoneOffset.UTC).plus(SCHEDULE_DELAY);
    return String.format(
        "%d %d %d %d *",
        scheduledTime.getMinute(),
        scheduledTime.getHour(),
        scheduledTime.getDayOfMonth(),
        scheduledTime.getMonthValue());
  }
}
