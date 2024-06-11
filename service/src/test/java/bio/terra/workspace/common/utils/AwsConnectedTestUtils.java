package bio.terra.workspace.common.utils;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.mocks.MockWorkspaceV2Api;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// Test Utils for AWS connected tests
@Component
public class AwsConnectedTestUtils {
  @Autowired private AwsConfiguration awsConfiguration;
  @Autowired MockWorkspaceV2Api mockWorkspaceV2Api;

  private Environment environment;

  public AwsConnectedTestUtils(AwsConfiguration awsConfiguration) {
    this.awsConfiguration = awsConfiguration;
  }

  private void initEnvironment() throws IOException {
    if (environment == null) {
      environment = AwsUtils.createEnvironmentDiscovery(awsConfiguration).discoverEnvironment();
    }
  }

  public Environment getEnvironment() throws IOException {
    initEnvironment();
    return environment;
  }

  public AwsCloudContext getAwsCloudContext() throws IOException {
    initEnvironment();
    return new AwsCloudContext(
        new AwsCloudContextFields(
            environment.getMetadata().getMajorVersion(),
            environment.getMetadata().getOrganizationId(),
            environment.getMetadata().getAccountId(),
            environment.getMetadata().getTenantAlias(),
            environment.getMetadata().getEnvironmentAlias(),
            /*workspaceSecurityGroups*/ null),
        new CloudContextCommonFields(
            WorkspaceFixtures.DEFAULT_GCP_SPEND_PROFILE_ID,
            WsmResourceState.READY,
            /* flightId= */ null,
            /* error= */ null));
  }
}
