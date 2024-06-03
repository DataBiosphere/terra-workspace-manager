package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("azure-unit")
public class GetActionManagedIdentityStepTest extends BaseMockitoStrictStubbingTest {
  @Mock private FlightContext mockFlightContext;
  @Mock private FlightMap mockWorkingMap;
  @Mock private SamService mockSamService;
  @Mock private WorkspaceService mockWorkspaceService;
  private final UUID testWorkspaceId = UUID.randomUUID();
  private final String testBillingProfileId = "testBillingProfile";
  private final SpendProfileId testSpendProfile = new SpendProfileId(testBillingProfileId);
  @Mock private AuthenticatedUserRequest mockAuthenticatedUserRequest;

  @Mock private Workspace mockWorkspace;

  @Test
  void testSuccess() throws InterruptedException {
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    Optional<String> fetchedIdentity = Optional.of("this/is/an/actionIdentity-1234");
    String expectedIdentityNameInContext = "actionIdentity-1234";
    when(mockSamService.getActionIdentityForUser(
            SamConstants.SamResource.PRIVATE_AZURE_CONTAINER_REGISTRY,
            SamConstants.SamPrivateAzureContainerRegistryAction.PULL_IMAGE,
            testBillingProfileId,
            mockAuthenticatedUserRequest))
        .thenReturn(fetchedIdentity);
    when(mockWorkspace.spendProfileId()).thenReturn(testSpendProfile);
    when(mockWorkspaceService.getWorkspace(testWorkspaceId)).thenReturn(mockWorkspace);

    var step =
        new GetActionManagedIdentityStep(
            mockSamService, mockWorkspaceService, testWorkspaceId, mockAuthenticatedUserRequest);

    assertThat(step.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));

    verify(mockWorkingMap)
        .put(GetActionManagedIdentityStep.ACTION_IDENTITY, expectedIdentityNameInContext);
  }

  @Test
  void testNoIdentityFound() throws InterruptedException {
    Optional<String> fetchedIdentity = Optional.empty();
    when(mockSamService.getActionIdentityForUser(
            SamConstants.SamResource.PRIVATE_AZURE_CONTAINER_REGISTRY,
            SamConstants.SamPrivateAzureContainerRegistryAction.PULL_IMAGE,
            testBillingProfileId,
            mockAuthenticatedUserRequest))
        .thenReturn(fetchedIdentity);
    when(mockWorkspace.spendProfileId()).thenReturn(testSpendProfile);
    when(mockWorkspaceService.getWorkspace(testWorkspaceId)).thenReturn(mockWorkspace);
    var step =
        new GetActionManagedIdentityStep(
            mockSamService, mockWorkspaceService, testWorkspaceId, mockAuthenticatedUserRequest);

    // Step should succeed, but nothing should have been inserted into flight context
    assertThat(step.doStep(mockFlightContext), equalTo(StepResult.getStepResultSuccess()));
    verify(mockWorkingMap, never())
        .put(eq(GetActionManagedIdentityStep.ACTION_IDENTITY), any(String.class));
  }
}
