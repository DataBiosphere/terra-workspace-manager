package bio.terra.workspace.service.azurecompute;

import bio.terra.cloudres.azure.resourcemanager.common.ApplicationSecretCredentials;
import bio.terra.cloudres.azure.resourcemanager.resources.ResourceManagerCow;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.workspace.service.azurecompute.exceptions.DeploymentFailedException;
import bio.terra.workspace.service.azurecompute.model.CreateDeploymentRequest;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.SyncPoller;
import com.azure.resourcemanager.resources.fluentcore.model.Accepted;
import com.azure.resourcemanager.resources.models.Deployment;
import com.azure.resourcemanager.resources.models.DeploymentMode;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AzureComputeService {
  private final Logger logger = LoggerFactory.getLogger(AzureComputeService.class);

  // TODO credentials
  @Autowired
  public AzureComputeService() {}

  /** The client name required by CRL. */
  private static final String CLIENT_NAME = "azurecompute";

  /** How long to keep the resource before Janitor does the cleanup. */
  private static final Duration TEST_RESOURCE_TIME_TO_LIVE = Duration.ofHours(1);

  // TODO replace me
  private static final UUID APPLICATION_ID =
      UUID.fromString("51ef1ef2-b571-43b3-bcb3-ecaa3b49f0ed");
  private static final UUID HOME_TENANT_ID =
      UUID.fromString("fad90753-2022-4456-9b0a-c7e5b934e408");
  private static final String SECRET = "v6828GxdSEXKPR5.oAUlEVAhv2_u_f8J~J";
  private static final UUID TENANT_ID = UUID.fromString("0cb7a640-45a2-4ed6-be9f-63519f86e04b");
  private static final UUID SUBSCRIPTION_ID =
      UUID.fromString("3efc5bdf-be0e-44e7-b1d7-c08931e3c16c");
  private static final String RESOURCE_GROUP = "mrg-rtitle-1-previe-20210819100327";
  //    private static final UUID APPLICATION_ID = UUID.fromString("app-id");
  //    private static final UUID HOME_TENANT_ID = UUID.fromString("home-tenant-id");
  //    private static final String SECRET = "secret";
  //    private static final UUID TENANT_ID = UUID.fromString("tenant-id");
  //    private static final UUID SUBSCRIPTION_ID = UUID.fromString("subscription-id");
  //    private static final String RESOURCE_GROUP = "mrg-name";

  public static final String DEFAULT_CLIENT_NAME = "azure-compute-client";

  public static final ClientConfig DEFAULT_CLIENT_CONFIG =
      ClientConfig.Builder.newBuilder().setClient(DEFAULT_CLIENT_NAME).build();

  private ResourceManagerCow cow =
      ResourceManagerCow.create(
          DEFAULT_CLIENT_CONFIG,
          new AzureProfile(
              TENANT_ID.toString(), SUBSCRIPTION_ID.toString(), AzureEnvironment.AZURE),
          new ApplicationSecretCredentials(APPLICATION_ID, HOME_TENANT_ID, SECRET));

  public Accepted<Deployment> create(CreateDeploymentRequest request) throws IOException {

    // Deploy VM
    logger.info("Deploying VM...");
    Accepted<Deployment> deployment =
        cow.beginDeployTemplate(
            RESOURCE_GROUP,
            request.deploymentName,
            request.template,
            // TODO
            Map.of(
                "adminUsername",
                "terra",
                "authenticationType",
                "password",
                "adminPasswordOrKey",
                "terra123?"),
            DeploymentMode.COMPLETE);

    return deployment;
  }

  public Deployment pollUntilSuccess(Accepted<Deployment> deployment)
      throws DeploymentFailedException {
    logger.info("Waiting for deployment to finish...");
    SyncPoller<Void, Deployment> poller = deployment.getSyncPoller();
    PollResponse<Void> finalPollResponse = poller.waitForCompletion(Duration.ofMinutes(30));

    if (LongRunningOperationStatus.SUCCESSFULLY_COMPLETED != finalPollResponse.getStatus()) {
      throw new DeploymentFailedException(
          "The deployment was not in successfully completed status after timeout. Deployment's final status: "
              + finalPollResponse.getStatus());
    }

    // Verify result
    return poller.getFinalResult();
  }
}
