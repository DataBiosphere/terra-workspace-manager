package bio.terra.workspace.common.utils;

import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest.AuthType;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("azure-test")
@Component
public class AzureTestUtils {
  private final AzureTestConfiguration azureTestConfiguration;

  public AzureTestUtils(AzureTestConfiguration azureTestConfiguration) {
    this.azureTestConfiguration = azureTestConfiguration;
  }

  /** Expose the default test user email. */
  public String getDefaultUserEmail() {
    return azureTestConfiguration.getDefaultUserEmail();
  }

  /** Expose the second test user email. */
  public String getSecondUserEmail() {
    return azureTestConfiguration.getSecondUserEmail();
  }

  /** Provides an AuthenticatedUserRequest using the default user's email and access token. */
  public AuthenticatedUserRequest defaultUserAuthRequest() {
    return new AuthenticatedUserRequest()
        .email(getDefaultUserEmail())
        .subjectId(azureTestConfiguration.getDefaultUserObjectId())
        .authType(AuthType.BASIC);
  }

  /** Provides an AuthenticatedUserRequest using the second user's email and access token. */
  public AuthenticatedUserRequest secondUserAuthRequest() {
    return new AuthenticatedUserRequest()
        .email(getSecondUserEmail())
        .subjectId(azureTestConfiguration.getSecondUserObjectId())
        .authType(AuthType.BASIC);
  }

  public AzureCloudContext getAzureCloudContext() {
    return new AzureCloudContext(
        azureTestConfiguration.getTenantId(),
        azureTestConfiguration.getSubscriptionId(),
        azureTestConfiguration.getManagedResourceGroupId());
  }
}
