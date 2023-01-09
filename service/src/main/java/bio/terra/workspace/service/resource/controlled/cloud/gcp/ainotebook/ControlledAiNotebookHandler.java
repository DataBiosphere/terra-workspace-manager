package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAcceleratorConfig;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.notebooks.v1.model.AcceleratorConfig;
import com.google.api.services.notebooks.v1.model.Instance;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@SuppressFBWarnings(
    value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
    justification = "Enable both injection and static lookup")
@Component
public class ControlledAiNotebookHandler implements WsmResourceHandler {

  private static final int MAX_INSTANCE_NAME_LENGTH = 63;
  private static ControlledAiNotebookHandler theHandler;
  private final GcpCloudContextService gcpCloudContextService;
  private final CrlService crlService;

  @Autowired
  public ControlledAiNotebookHandler(
      CrlService crlService, GcpCloudContextService gcpCloudContextService) {
    this.crlService = crlService;
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
    InstanceName instanceName =
        InstanceName.builder()
            .projectId(projectId)
            .location(attributes.getLocation())
            .instanceId(attributes.getInstanceId())
            .build();
    String machineType = null;
    ApiGcpAiNotebookInstanceAcceleratorConfig acceleratorConfig =
        new ApiGcpAiNotebookInstanceAcceleratorConfig();
    try {
      Instance instance =
          crlService.getAIPlatformNotebooksCow().instances().get(instanceName).execute();
      machineType = instance.getMachineType();
      if (instance.getAcceleratorConfig() != null) {
        AcceleratorConfig accelerator = instance.getAcceleratorConfig();
        acceleratorConfig.setType(accelerator.getType());
        acceleratorConfig.setCoreCount(accelerator.getCoreCount());
      }
    } catch (IOException e) {
      // When a notebook instance is just deleted in GCP in a deletion flight,
      // there will be a moment that we need to access this instance to delete
      // it on SAM
      if (!e.getMessage().contains("404 Not Found")) {
        throw new InternalServerErrorException("IOException", e);
      }
    }

    var resource =
        ControlledAiNotebookInstanceResource.builder()
            .common(new ControlledResourceFields(dbResource))
            .instanceId(attributes.getInstanceId())
            .location(attributes.getLocation())
            .projectId(projectId)
            .machineType(machineType)
            .acceleratorConfig(acceleratorConfig)
            .build();
    return resource;
  }

  /**
   * Directly calling the gcp api to get/update instance, requires the createComputeService, see the
   * example in https://cloud.google.com/compute/docs/reference/rest/v1/instances/get
   */
  public static Compute createComputeService() throws IOException, GeneralSecurityException {
    HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

    GoogleCredential credential = GoogleCredential.getApplicationDefault();
    if (credential.createScopedRequired()) {
      credential =
          credential.createScoped(Arrays.asList("https://www.googleapis.com/auth/cloud-platform"));
    }

    return new Compute.Builder(httpTransport, jsonFactory, credential)
        .setApplicationName("Google-ComputeSample/0.1")
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

    /**
     * The regular expression only allow legal character combinations which start with lowercase
     * letter, lowercase letter and numbers and dash("-") in the string, and lowercase letter and
     * number at the end of the string. It trims any other combinations.
     */
    generatedName = generatedName.replaceAll("[^a-z0-9-]+|^[^a-z]+|[^a-z0-9]+$", "");
    return generatedName;
  }
}
