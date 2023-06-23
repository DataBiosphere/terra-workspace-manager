package bio.terra.workspace.common.utils;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("aws-connected-test")
@Component
public class AwsTestUtils {
  @Autowired private AwsConfiguration awsConfiguration;

  @Autowired MvcWorkspaceApi mvcWorkspaceApi;

  private Environment environment;

  public AwsTestUtils(AwsConfiguration awsConfiguration) {
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
            new SpendProfileId("spend-profile-id"),
            WsmResourceState.READY,
            /*flightId=*/ null,
            /*error=*/ null));
  }
}
