package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.api.services.common.Defaults;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.RetryUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.notebooks.v1.AIPlatformNotebooks;
import com.google.api.services.notebooks.v1.AIPlatformNotebooksScopes;
import com.google.api.services.notebooks.v1.model.AcceleratorConfig;
import com.google.api.services.notebooks.v1.model.SetInstanceAcceleratorRequest;
import com.google.api.services.notebooks.v1.model.SetInstanceMachineTypeRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.notebooks.v1.Instance;
import com.google.cloud.notebooks.v1.NotebookServiceClient;
import com.google.cloud.notebooks.v1.SetInstanceMachineTypeRequestOrBuilder;
import com.google.rpc.context.AttributeContext;
import org.apache.tomcat.jni.Time;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_ACCELERATOR_CONFIG;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_MACHINE_TYPE;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_ACCELERATOR_CONFIG;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_MACHINE_TYPE;

/**
 * Make a direct cloud call using the Google API Client Library for AI notebooks {@link
 * AIPlatformNotebooks}.
 */
public class UpdateAiNotebookCpuAndGpuStep implements Step {
  private final ControlledAiNotebookInstanceResource resource;
  private final ClientConfig clientConfig;
  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;

  private final GcpCloudContextService gcpCloudContextService;

  public UpdateAiNotebookCpuAndGpuStep(
      ControlledAiNotebookInstanceResource resource,
      ClientConfig clientConfig,
      SamService samService,
      AuthenticatedUserRequest userRequest,
      GcpCloudContextService gcpCloudContextService) {
    this.resource = resource;
    this.clientConfig = clientConfig;
    this.samService = samService;
    this.userRequest = userRequest;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // TODO (aaronwa@): Merge check stopped/ update needed step here.
    String machineType = context.getInputParameters().get(UPDATE_MACHINE_TYPE, String.class);
    AcceleratorConfig acceleratorConfig =
        context.getInputParameters().get(UPDATE_ACCELERATOR_CONFIG, AcceleratorConfig.class);
    // TODO (aaronwa@): place in working map or input param?
    String projectId = resource.getProjectId();
    InstanceName instanceName = resource.toInstanceName(projectId);
    return updateAiNotebookCpuAndGpu(
        projectId,
        instanceName.location(),
        instanceName.instanceId(),
        machineType,
        acceleratorConfig);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    String previousMachineType = context.getWorkingMap().get(PREVIOUS_MACHINE_TYPE, String.class);
    AcceleratorConfig previousAcceleratorConfig =
        context.getWorkingMap().get(PREVIOUS_ACCELERATOR_CONFIG, AcceleratorConfig.class);
    String projectId = resource.getProjectId();
    InstanceName instanceName = resource.toInstanceName(projectId);
    return updateAiNotebookCpuAndGpu(
        projectId,
        instanceName.location(),
        instanceName.instanceId(),
        previousMachineType,
        previousAcceleratorConfig);
  }

  private StepResult updateAiNotebookCpuAndGpu(
      String projectId,
      String location,
      String instanceId,
      String machineType,
      AcceleratorConfig acceleratorConfig) {
    try {
      // Do the updating.
      GoogleCredentials creds =
          GoogleCredentials.getApplicationDefault().createScoped(AIPlatformNotebooksScopes.all());

      var aaron2proj = gcpCloudContextService.getRequiredGcpProject(resource.getWorkspaceId());

      //      if (aaron2.equals(projectId)) {
      //
      //      }

      String petSaEmail =
          SamRethrow.onInterrupted(
              () -> samService.getOrCreatePetSaEmail(aaron2proj, userRequest.getRequiredToken()),
              "aaron");

      //      NotebookServiceClient

      //      List<String> aaronList = new ArrayList<String>(AIPlatformNotebooksScopes.all());
      //
      //      ImpersonatedCredentials targetCredentials =
      //          ImpersonatedCredentials.create(creds, petSaEmail, null, aaronList, 600);
      //      var notebooks =
      //          new AIPlatformNotebooks.Builder(
      //                  Defaults.httpTransport(),
      //                  Defaults.jsonFactory(),
      //                  new HttpCredentialsAdapter(creds))
      //              //              .setApplicationName(clientConfig.getClientName())
      //              .build();

      //      instance.setServiceAccount(serviceAccountEmail);
      //      instance.setServiceAccountScopes(SERVICE_ACCOUNT_SCOPES);

      //      ProjectName parent = ProjectName.of(destinationProjectId);
      //      String petSaEmail =
      //          SamRethrow.onInterrupted(
      //              () ->
      //                  samService.getOrCreatePetSaEmail(
      //                      gcpCloudContextService.getRequiredGcpProject(destinationWorkspaceId),
      //                      userRequest.getRequiredToken()),
      //              "CopyBigQueryDatasetDifferentRegionStep");
      //
      //      TransferConfig config =
      //          dataTransferServiceClient.createTransferConfig(
      //              CreateTransferConfigRequest.newBuilder()
      //                  .setParent(parent.toString())
      //                  .setTransferConfig(transferConfig)
      //                  .setServiceAccountName(petSaEmail)
      //                  .build());

      try (NotebookServiceClient notebookServiceClient = NotebookServiceClient.create()) {
        InstanceName instanceName = resource.toInstanceName(projectId);

        //        notebookServiceClient.getSettings();
        //        Instance getInstance =
        // notebookServiceClient.getInstance(instanceName.formatName());

        if (machineType != null) {
          var aaron2 =
              RetryUtils.getWithRetryOnException(
                  () ->
                      notebookServiceClient
                          .setInstanceMachineTypeAsync(
                              com.google.cloud.notebooks.v1.SetInstanceMachineTypeRequest
                                  .newBuilder()
                                  .setName(instanceName.formatName())
                                  .setMachineType(machineType)
                                  .build())
                          .get());
        }

        if (acceleratorConfig != null) {
          var aaron =
              RetryUtils.getWithRetryOnException(
                  () ->
                      notebookServiceClient
                          .setInstanceAcceleratorAsync(
                              com.google.cloud.notebooks.v1.SetInstanceAcceleratorRequest
                                  .newBuilder()
                                  .setName(instanceName.formatName())
                                  .setCoreCount(acceleratorConfig.getCoreCount())
                                  .setType(
                                      Instance.AcceleratorType.valueOf(acceleratorConfig.getType()))
                                  .build())
                          .get());
        }

      } catch (ExecutionException | InterruptedException e) {
        //        throw new RuntimeException(e);
        //        Thread.sleep(1000 * 5);
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

    } catch (GoogleJsonResponseException e) {
      if (HttpStatus.BAD_REQUEST.value() == e.getStatusCode()
          || HttpStatus.NOT_FOUND.value() == e.getStatusCode()) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }
}
