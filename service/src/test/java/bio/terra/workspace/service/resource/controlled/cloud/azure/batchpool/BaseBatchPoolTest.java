package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetManagedIdentityStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.batch.BatchManager;
import com.azure.resourcemanager.batch.models.BatchAccount;
import com.azure.resourcemanager.batch.models.BatchAccounts;
import com.azure.resourcemanager.batch.models.DeploymentConfiguration;
import com.azure.resourcemanager.batch.models.ImageReference;
import com.azure.resourcemanager.batch.models.Pools;
import com.azure.resourcemanager.batch.models.VirtualMachineConfiguration;
import com.azure.resourcemanager.msi.MsiManager;
import java.util.UUID;
import org.mockito.Mock;

public class BaseBatchPoolTest extends BaseAzureSpringBootUnitTest {
  public static final UUID BATCH_POOL_ID = UUID.randomUUID();
  public static final String BATCH_POOL_VM_SIZE = "Standard_D2s_v3";
  public static final String BATCH_POOL_DISPLAY_NAME = "batchPoolDisplayName";
  public static final String BATCH_POOL_RESOURCE_DESCRIPTION = "description";

  public static final DeploymentConfiguration DEPLOYMENT_CONFIGURATION =
      new DeploymentConfiguration()
          .withVirtualMachineConfiguration(
              new VirtualMachineConfiguration()
                  .withImageReference(
                      new ImageReference()
                          .withOffer("ubuntuserver")
                          .withSku("18.04-lts")
                          .withPublisher("canonical")));
  public static final String BATCH_ACCOUNT_NAME = "sharedBatchAccount";
  public static final String AZURE_CLOUD_CONTEXT_RESOURCE_GROUP_ID = "resourceGroup";
  public static final String TENANT_ID = "tenantId";
  public static final String SUBSCRIPTION_ID = "subscriptionId";

  @Mock public AzureConfiguration mockAzureConfig;
  @Mock public CrlService mockCrlService;
  @Mock public LandingZoneBatchAccountFinder mockLandingZoneBatchAccountFinder;
  @Mock public FlightContext mockFlightContext;
  @Mock public AuthenticatedUserRequest mockAuthenticatedUserRequest;
  @Mock public FlightMap mockFlightMap;
  @Mock public FlightMap mockWorkingMap;
  @Mock public BatchManager mockBatchManager;
  @Mock public MsiManager mockMsiManager;
  @Mock public BatchAccounts mockBatchAccounts;
  @Mock public BatchAccount mockBatchAccount;
  @Mock public Pools mockPools;
  @Mock public AzureCloudContext mockAzureCloudContext;

  protected ControlledAzureBatchPoolResource resource;

  protected ControlledAzureBatchPoolResource.Builder buildDefaultResourceBuilder() {
    return ControlledAzureResourceFixtures.getAzureBatchPoolResourceBuilder(
        BATCH_POOL_ID,
        BATCH_POOL_DISPLAY_NAME,
        BATCH_POOL_VM_SIZE,
        DEPLOYMENT_CONFIGURATION,
        BATCH_POOL_RESOURCE_DESCRIPTION);
  }

  protected void setupBaseMocks() {
    // setup cloud context
    when(mockAzureCloudContext.getAzureResourceGroupId())
        .thenReturn(AZURE_CLOUD_CONTEXT_RESOURCE_GROUP_ID);
    when(mockAzureCloudContext.getAzureTenantId()).thenReturn(TENANT_ID);
    when(mockAzureCloudContext.getAzureSubscriptionId()).thenReturn(SUBSCRIPTION_ID);


    // setup auth request
    when(mockAuthenticatedUserRequest.getRequiredToken()).thenReturn("FAKE_TOKEN");

    // setup flight context
    when(mockFlightMap.get(
            eq(JobMapKeys.AUTH_USER_INFO.getKeyName()), eq(AuthenticatedUserRequest.class)))
        .thenReturn(mockAuthenticatedUserRequest);
    when(mockWorkingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
            AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);
    when(mockWorkingMap.get(GetManagedIdentityStep.MANAGED_IDENTITY_NAME, String.class)).thenReturn(BatchPoolFixtures.IDENTITY_NAME);
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockFlightContext.getInputParameters()).thenReturn(mockFlightMap);
  }
}
