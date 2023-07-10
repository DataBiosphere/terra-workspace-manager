package bio.terra.workspace.common.utils;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// Test Utils for connected tests
@Component
public class AwsConnectedTestUtils {
  @Autowired private AwsConfiguration awsConfiguration;
  @Autowired MvcWorkspaceApi mvcWorkspaceApi;

  private Environment environment;

  public AwsConnectedTestUtils(AwsConfiguration awsConfiguration) {
    this.awsConfiguration = awsConfiguration;
  }

  public AwsCloudContext getAwsCloudContext() throws IOException {
    if (environment == null) {
      environment = AwsUtils.createEnvironmentDiscovery(awsConfiguration).discoverEnvironment();
    }

    return new AwsCloudContext(
        new AwsCloudContextFields(
            environment.getMetadata().getMajorVersion(),
            environment.getMetadata().getOrganizationId(),
            environment.getMetadata().getAccountId(),
            environment.getMetadata().getTenantAlias(),
            environment.getMetadata().getEnvironmentAlias()),
        new CloudContextCommonFields(
            WorkspaceFixtures.DEFAULT_SPEND_PROFILE_ID,
            WsmResourceState.READY,
            /*flightId=*/ null,
            /*error=*/ null));
  }
}
