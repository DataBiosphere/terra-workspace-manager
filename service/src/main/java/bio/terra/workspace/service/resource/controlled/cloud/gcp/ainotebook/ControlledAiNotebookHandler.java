package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.workspace.common.utils.RetryUtils;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.notebooks.v1.GetInstanceRequest;
import com.google.cloud.notebooks.v1.Instance;
import com.google.cloud.notebooks.v1.NotebookServiceClient;
import com.google.common.base.CharMatcher;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.PostConstruct;
import javax.ws.rs.BadRequestException;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static bio.terra.workspace.common.utils.RetryUtils.DEFAULT_RETRY_FACTOR_INCREASE;
import static bio.terra.workspace.common.utils.RetryUtils.DEFAULT_RETRY_SLEEP_DURATION;
import static bio.terra.workspace.common.utils.RetryUtils.DEFAULT_RETRY_SLEEP_DURATION_MAX;

@SuppressFBWarnings(
    value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
    justification = "Enable both injection and static lookup")
@Component
public class ControlledAiNotebookHandler implements WsmResourceHandler {

  private static final int MAX_INSTANCE_NAME_LENGTH = 63;
  private static ControlledAiNotebookHandler theHandler;
  private final GcpCloudContextService gcpCloudContextService;

  @Autowired
  public ControlledAiNotebookHandler(GcpCloudContextService gcpCloudContextService) {
    this.gcpCloudContextService = gcpCloudContextService;
  }

  public static ControlledAiNotebookHandler getHandler() {
    return theHandler;
  }

  @PostConstruct
  public void init() {
    theHandler = this;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    // Old version of attributes do not have project id, so in that case we look it up
    ControlledAiNotebookInstanceAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAiNotebookInstanceAttributes.class);
    String projectId =
        Optional.ofNullable(attributes.getProjectId())
            .orElse(gcpCloudContextService.getRequiredGcpProject(dbResource.getWorkspaceId()));

    // Notebook attributes created prior to PF-2323 do not have machineType or acceleratorConfig
    // stored.
    // Thus, retrieve these two values from the cloud.
    String retrievedMachineType = attributes.getMachineType();
    AcceleratorConfig retrievedAcceleratorConfig = attributes.getAcceleratorConfig();

    if (retrievedMachineType == null || retrievedAcceleratorConfig == null) {
      try (NotebookServiceClient notebookServiceClient = NotebookServiceClient.create()) {
        InstanceName instanceName =
            InstanceName.builder()
                .instanceId(attributes.getInstanceId())
                .projectId(attributes.getProjectId())
                .location(attributes.getLocation())
                .build();

        List<Class<? extends Exception>> retryableErrors = new ArrayList<>();
        retryableErrors.add(ApiException.class);
        Instance cloudInstance =
            RetryUtils.getWithRetryOnException(
                () ->
                    notebookServiceClient.getInstance(
                        GetInstanceRequest.newBuilder().setName(instanceName.formatName()).build()),
                Duration.ofMinutes(5),
                DEFAULT_RETRY_SLEEP_DURATION,
                DEFAULT_RETRY_FACTOR_INCREASE,
                DEFAULT_RETRY_SLEEP_DURATION_MAX,
                retryableErrors);

        retrievedMachineType = cloudInstance.getMachineType();
        retrievedAcceleratorConfig =
            new AcceleratorConfig(
                cloudInstance.getMachineType(),
                cloudInstance.getAcceleratorConfig().getCoreCount());

      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    return ControlledAiNotebookInstanceResource.builder()
        .common(new ControlledResourceFields(dbResource))
        .instanceId(attributes.getInstanceId())
        .location(attributes.getLocation())
        .projectId(projectId)
        .machineType(retrievedMachineType)
        .acceleratorConfig(retrievedAcceleratorConfig)
        .build();
  }

  /**
   * Generate Ai notebook cloud instance id that meets the requirements for a valid instance id.
   *
   * <p>Instance name id must match the regex '(?:[a-z](?:[-a-z0-9]{0,63}[a-z0-9])?)', i.e. starting
   * with a lowercase alpha character, only alphanumerics and '-' of max length 63. I don't have a
   * documentation link, but gcloud will complain otherwise.
   */
  public String generateCloudName(@Nullable UUID workspaceUuid, String aiNotebookName) {
    String generatedName = aiNotebookName.toLowerCase();
    generatedName =
        generatedName.length() > MAX_INSTANCE_NAME_LENGTH
            ? generatedName.substring(0, MAX_INSTANCE_NAME_LENGTH)
            : generatedName;

    // AI notebook name only allows numbers, dash("-"), and lower case letters.
    generatedName =
        CharMatcher.inRange('0', '9')
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.is('-'))
            .retainFrom(generatedName);
    // The name cannot start or end with dash("-")
    generatedName = CharMatcher.is('-').trimFrom(generatedName);
    // The name cannot start with number.
    generatedName = CharMatcher.inRange('0', '9').trimLeadingFrom(generatedName);
    if (generatedName.length() == 0) {
      throw new BadRequestException(
          String.format(
              "Cannot generate a valid AI notebook name from %s, it must contains"
                  + " alphanumerical characters.",
              aiNotebookName));
    }
    return generatedName;
  }
}
