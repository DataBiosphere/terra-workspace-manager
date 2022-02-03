package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@SuppressFBWarnings(
    value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
    justification = "Enable both injection and static lookup")
@Component
public class ControlledBigQueryDatasetHandler implements WsmResourceHandler {
  private static ControlledBigQueryDatasetHandler theHandler;
  private final GcpCloudContextService gcpCloudContextService;

  @Autowired
  public ControlledBigQueryDatasetHandler(GcpCloudContextService gcpCloudContextService) {
    this.gcpCloudContextService = gcpCloudContextService;
  }

  public static ControlledBigQueryDatasetHandler getHandler() {
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
    ControlledBigQueryDatasetAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledBigQueryDatasetAttributes.class);
    String projectId =
        Optional.ofNullable(attributes.getProjectId())
            .orElse(gcpCloudContextService.getRequiredGcpProject(dbResource.getWorkspaceId()));

    var resource =
        ControlledBigQueryDatasetResource.builder()
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
            .datasetName(attributes.getDatasetName())
            .projectId(projectId)
            .build();
    resource.validate();
    return resource;
  }
}
