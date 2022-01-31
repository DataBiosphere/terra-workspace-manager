package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
            .workspaceId(dbResource.getWorkspaceId())
            .resourceId(dbResource.getResourceId())
            .name(dbResource.getName().orElse(null))
            .description(dbResource.getDescription().orElse(null))
            .cloningInstructions(dbResource.getCloningInstructions())
            .assignedUser(dbResource.getAssignedUser().orElse(null))
            .privateResourceState(dbResource.getPrivateResourceState().orElse(null))
            .accessScope(dbResource.getAccessScope().orElse(null))
            .managedBy(dbResource.getManagedBy().orElse(null))
            .applicationId(dbResource.getApplicationId().orElse(null))
            .instanceId(attributes.getInstanceId())
            .location(attributes.getLocation())
            .projectId(projectId)
            .build();
    resource.validate();
    return resource;
  }
}
