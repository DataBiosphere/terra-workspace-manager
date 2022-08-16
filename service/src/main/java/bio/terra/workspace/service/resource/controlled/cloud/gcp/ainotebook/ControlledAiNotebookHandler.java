package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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

    var resource =
        ControlledAiNotebookInstanceResource.builder()
            .common(new ControlledResourceFields(dbResource))
            .instanceId(attributes.getInstanceId())
            .location(attributes.getLocation())
            .projectId(projectId)
            .build();
    return resource;
  }

  public String generateCloudName(@Nullable UUID workspaceUuid, String aiNotebookName) {
    return aiNotebookName;
  }
}
