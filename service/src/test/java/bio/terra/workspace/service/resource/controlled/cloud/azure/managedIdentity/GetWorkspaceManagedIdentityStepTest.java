package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.msi.models.Identities;
import com.azure.resourcemanager.msi.models.Identity;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;

@Tag("azure-unit")
public class GetWorkspaceManagedIdentityStepTest extends BaseMockitoStrictStubbingTest {
  @Mock private FlightContext mockFlightContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private CrlService mockCrlService;
  @Mock private MsiManager mockMsiManager;
  @Mock private Identities mockIdentities;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private HttpResponse mockHttpResponse;
  @Mock private ResourceDao mockResourceDao;
  @Mock private Identity mockIdentity;

  @Test
  void testSuccess() throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();
    var identityResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, workspaceId)
            .build();

    createMockFlightContext();

    when(mockCrlService.getMsiManager(any(), any())).thenReturn(mockMsiManager);
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    when(mockIdentities.getByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(),
            identityResource.getManagedIdentityName()))
        .thenReturn(mockIdentity);
    when(mockResourceDao.getResourceByName(workspaceId, identityResource.getName()))
        .thenReturn(identityResource);
    when(mockIdentity.name()).thenReturn(UUID.randomUUID().toString());
    when(mockIdentity.principalId()).thenReturn(UUID.randomUUID().toString());
    when(mockIdentity.clientId()).thenReturn(UUID.randomUUID().toString());

    var step =
        new GetWorkspaceManagedIdentityStep(
            mockAzureConfig,
            mockCrlService,
            workspaceId,
            mockResourceDao,
            identityResource.getName());
    assertThat(step.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    verify(mockWorkingMap).put(GetManagedIdentityStep.MANAGED_IDENTITY_NAME, mockIdentity.name());
    verify(mockWorkingMap)
        .put(GetManagedIdentityStep.MANAGED_IDENTITY_PRINCIPAL_ID, mockIdentity.principalId());
    verify(mockWorkingMap)
        .put(GetManagedIdentityStep.MANAGED_IDENTITY_CLIENT_ID, mockIdentity.clientId());
  }

  @Test
  void testSuccessWithIdentityIdInsteadOfName() throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();
    var identityResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, workspaceId)
            .build();

    createMockFlightContext();

    when(mockCrlService.getMsiManager(any(), any())).thenReturn(mockMsiManager);
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    when(mockIdentities.getByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(),
            identityResource.getManagedIdentityName()))
        .thenReturn(mockIdentity);
    when(mockResourceDao.getResourceByName(
            workspaceId, identityResource.getResourceId().toString()))
        .thenThrow(new ResourceNotFoundException("not found"));
    when(mockResourceDao.getResource(workspaceId, identityResource.getResourceId()))
        .thenReturn(identityResource);
    when(mockIdentity.name()).thenReturn(UUID.randomUUID().toString());
    when(mockIdentity.principalId()).thenReturn(UUID.randomUUID().toString());
    when(mockIdentity.clientId()).thenReturn(UUID.randomUUID().toString());

    var step =
        new GetWorkspaceManagedIdentityStep(
            mockAzureConfig,
            mockCrlService,
            workspaceId,
            mockResourceDao,
            identityResource.getResourceId().toString());
    assertThat(step.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    verify(mockWorkingMap).put(GetManagedIdentityStep.MANAGED_IDENTITY_NAME, mockIdentity.name());
    verify(mockWorkingMap)
        .put(GetManagedIdentityStep.MANAGED_IDENTITY_PRINCIPAL_ID, mockIdentity.principalId());
    verify(mockWorkingMap)
        .put(GetManagedIdentityStep.MANAGED_IDENTITY_CLIENT_ID, mockIdentity.clientId());
  }

  @Test
  void testFatal() throws InterruptedException {
    StepResult stepResult = testWithError(HttpStatus.NOT_FOUND);
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  @Test
  void testRetry() throws InterruptedException {
    StepResult stepResult = testWithError(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_RETRY));
  }

  private StepResult testWithError(HttpStatus httpStatus) throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();
    var identityResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, workspaceId)
            .build();

    createMockFlightContext();

    when(mockCrlService.getMsiManager(any(), any())).thenReturn(mockMsiManager);
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    when(mockIdentities.getByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(),
            identityResource.getManagedIdentityName()))
        .thenThrow(new ManagementException(httpStatus.name(), mockHttpResponse));
    when(mockHttpResponse.getStatusCode()).thenReturn(httpStatus.value());
    when(mockResourceDao.getResourceByName(workspaceId, identityResource.getName()))
        .thenReturn(identityResource);

    var step =
        new GetWorkspaceManagedIdentityStep(
            mockAzureConfig,
            mockCrlService,
            workspaceId,
            mockResourceDao,
            identityResource.getName());
    return step.doStep(mockFlightContext);
  }

  private FlightContext createMockFlightContext() {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);

    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(UUID.randomUUID().toString());

    return mockFlightContext;
  }
}
