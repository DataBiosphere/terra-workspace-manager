package bio.terra.workspace.common.utils;

import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("aws-test")
@Component
public class AwsTestUtils {
  @Autowired final AwsConfiguration awsConfiguration;
  @Autowired private final UserAccessUtils userAccessUtils;

  public AwsTestUtils(AwsConfiguration awsConfiguration, UserAccessUtils userAccessUtils) {
    this.awsConfiguration = awsConfiguration;
    this.userAccessUtils = userAccessUtils;
  }

  public AwsConfiguration getAwsConfiguration() {
    return awsConfiguration;
  }

  public Workspace createWorkspace(WorkspaceService workspaceService) {
    UUID uuid = UUID.randomUUID();
    Workspace workspace =
        Workspace.builder()
            .workspaceId(uuid)
            .userFacingId(uuid.toString())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceService.createWorkspace(
        workspace, null, null, userAccessUtils.defaultUserAuthRequest());
    return workspace;
  }
}
