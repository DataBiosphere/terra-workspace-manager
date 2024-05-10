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
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
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
public class GetPetManagedIdentityStepTest extends BaseMockitoStrictStubbingTest {
  @Mock private FlightContext mockFlightContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private CrlService mockCrlService;
  @Mock private MsiManager mockMsiManager;
  @Mock private Identities mockIdentities;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private HttpResponse mockHttpResponse;
  @Mock private SamService mockSamService;
  private final String testEmail = UUID.randomUUID() + "@example.com";
  @Mock private Identity mockIdentity;

  @Mock private AuthenticatedUserRequest mockRequest;

  @Test
  void testSuccess() throws InterruptedException {
    createMockFlightContext();
    var identityId = UUID.randomUUID().toString();
    when(mockSamService.getOrCreateUserManagedIdentityForUser(
            testEmail,
            mockAzureCloudContext.getAzureSubscriptionId(),
            mockAzureCloudContext.getAzureTenantId(),
            mockAzureCloudContext.getAzureResourceGroupId()))
        .thenReturn(identityId);
    when(mockSamService.getUserEmailFromSam(mockRequest)).thenReturn(testEmail);
    when(mockCrlService.getMsiManager(any(), any())).thenReturn(mockMsiManager);
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    when(mockIdentities.getById(identityId)).thenReturn(mockIdentity);
    when(mockIdentity.name()).thenReturn(UUID.randomUUID().toString());
    when(mockIdentity.principalId()).thenReturn(UUID.randomUUID().toString());
    when(mockIdentity.clientId()).thenReturn(UUID.randomUUID().toString());

    var step =
        new GetPetManagedIdentityStep(mockAzureConfig, mockCrlService, mockSamService, testEmail);
    assertThat(step.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    verify(mockWorkingMap).put(GetManagedIdentityStep.MANAGED_IDENTITY_NAME, mockIdentity.name());
    verify(mockWorkingMap)
        .put(GetManagedIdentityStep.MANAGED_IDENTITY_PRINCIPAL_ID, mockIdentity.principalId());
    verify(mockWorkingMap)
        .put(GetManagedIdentityStep.MANAGED_IDENTITY_CLIENT_ID, mockIdentity.clientId());

    var stepWithRequest =
        new GetPetManagedIdentityStep(mockAzureConfig, mockCrlService, mockSamService, mockRequest);
    assertThat(
        stepWithRequest.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));
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
    createMockFlightContext();

    var identityId = UUID.randomUUID().toString();
    when(mockSamService.getOrCreateUserManagedIdentityForUser(
            testEmail,
            mockAzureCloudContext.getAzureSubscriptionId(),
            mockAzureCloudContext.getAzureTenantId(),
            mockAzureCloudContext.getAzureResourceGroupId()))
        .thenReturn(identityId);
    when(mockCrlService.getMsiManager(any(), any())).thenReturn(mockMsiManager);
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    when(mockIdentities.getById(identityId))
        .thenThrow(new ManagementException(httpStatus.name(), mockHttpResponse));
    when(mockHttpResponse.getStatusCode()).thenReturn(httpStatus.value());

    var step =
        new GetPetManagedIdentityStep(mockAzureConfig, mockCrlService, mockSamService, testEmail);
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
