package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.msi.models.Identity;
import java.util.Optional;
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
  @Mock private HttpResponse mockHttpResponse;
  @Mock private Identity mockIdentity;
  @Mock private ManagedIdentityHelper managedIdentityHelper;

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

    when(mockIdentity.name()).thenReturn(UUID.randomUUID().toString());
    when(mockIdentity.principalId()).thenReturn(UUID.randomUUID().toString());
    when(mockIdentity.clientId()).thenReturn(UUID.randomUUID().toString());
    when(managedIdentityHelper.getManagedIdentity(
            mockAzureCloudContext, workspaceId, identityResource.getName()))
        .thenReturn(Optional.of(mockIdentity));
    var step =
        new GetWorkspaceManagedIdentityStep(
            workspaceId,
            identityResource.getName(),
            MissingIdentityBehavior.FAIL_ON_MISSING,
            managedIdentityHelper);

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

  @Test
  void testSuccessOnMissingWhenConfigured() throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();
    var identityResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, workspaceId)
            .build();
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);

    var step =
        new GetWorkspaceManagedIdentityStep(
            workspaceId,
            identityResource.getName(),
            MissingIdentityBehavior.ALLOW_MISSING,
            managedIdentityHelper);
    assertThat(step.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  void testFailureOnMissingResourceWhenConfigured() throws InterruptedException {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();
    var identityResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, workspaceId)
            .build();
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);

    var step =
        new GetWorkspaceManagedIdentityStep(
            workspaceId,
            identityResource.getName(),
            MissingIdentityBehavior.FAIL_ON_MISSING,
            managedIdentityHelper);
    assertThat(
        step.doStep(mockFlightContext).getStepStatus(),
        equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
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

    when(mockHttpResponse.getStatusCode()).thenReturn(httpStatus.value());
    when(managedIdentityHelper.getManagedIdentity(
            mockAzureCloudContext, workspaceId, identityResource.getName()))
        .thenThrow(new ManagementException(httpStatus.name(), mockHttpResponse));

    var step =
        new GetWorkspaceManagedIdentityStep(
            workspaceId,
            identityResource.getName(),
            MissingIdentityBehavior.FAIL_ON_MISSING,
            managedIdentityHelper);
    return step.doStep(mockFlightContext);
  }

  private FlightContext createMockFlightContext() {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);

    return mockFlightContext;
  }
}
