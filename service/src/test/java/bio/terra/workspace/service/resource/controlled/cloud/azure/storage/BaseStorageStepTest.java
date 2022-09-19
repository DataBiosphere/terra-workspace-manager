package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.utils.ManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.StorageAccount;
import com.azure.resourcemanager.storage.models.StorageAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

/** Base class for storage account and storage container tests. */

public class BaseStorageStepTest extends BaseAzureTest {

  protected final String STUB_STRING_RETURN = "stubbed-return";

  @Mock protected FlightContext mockFlightContext;
  @Mock protected CrlService mockCrlService;
  @Mock protected AzureConfiguration mockAzureConfig;
  @Mock protected AzureCloudContext mockAzureCloudContext;
  @Mock protected StorageManager mockStorageManager;
  @Mock protected StorageAccounts mockStorageAccounts;
  @Mock protected StorageAccount mockStorageAccount;
  @Mock protected FlightMap mockWorkingMap;
  protected final ManagementException resourceNotFoundException =
      new ManagementException(
          "Resource was not found.",
          /*response=*/ null,
          new ManagementError(
              ManagementExceptionUtils.RESOURCE_NOT_FOUND, "Resource was not found."));

  @BeforeEach
  public void setup() {
    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(mockCrlService.getStorageManager(mockAzureCloudContext, mockAzureConfig))
        .thenReturn(mockStorageManager);

    when(mockStorageManager.storageAccounts()).thenReturn(mockStorageAccounts);

    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);
  }
}
