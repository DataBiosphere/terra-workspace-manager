package bio.terra.workspace.service.workspace.flight.azure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.cloud.azure.DeleteControlledAzureResourcesStep;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class DeleteControlledAzureResourcesStepTest extends BaseAzureUnitTest {
  @Mock private ControlledResourceService mockControlledResourceService;
  @Mock private ResourceDao mockResourceDao;
  @Mock private SamService mockSamService;
  @Mock private FlightContext mockFlightContext;

  @Test
  void testRequiresDeleteAction() throws InterruptedException, RetryException {
    UUID workspaceId = UUID.randomUUID();
    AuthenticatedUserRequest userRequest =
        new AuthenticatedUserRequest("user@example.com", "subjectId", Optional.empty());
    ControlledResource deleteMe =
        ControlledAzureStorageContainerResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceId)
                    .resourceId(UUID.randomUUID())
                    .name("deleteMe")
                    .cloningInstructions(CloningInstructions.COPY_NOTHING)
                    .createdByEmail(userRequest.getEmail())
                    .managedBy(ManagedByType.MANAGED_BY_USER)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                    .build())
            .storageContainerName("user-storage-container")
            .build();
    ControlledResource cannotDeleteMe =
        ControlledAzureStorageContainerResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceId)
                    .resourceId(UUID.randomUUID())
                    .name("cannotDeleteMe")
                    .cloningInstructions(CloningInstructions.COPY_NOTHING)
                    .createdByEmail("application@example.com")
                    .managedBy(ManagedByType.MANAGED_BY_APPLICATION)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                    .build())
            .storageContainerName("application-storage-container")
            .build();
    doReturn(List.of(deleteMe, cannotDeleteMe))
        .when(mockResourceDao)
        .listControlledResources(workspaceId, CloudPlatform.AZURE);
    doReturn(true)
        .when(mockSamService)
        .isAuthorized(
            userRequest,
            deleteMe.getCategory().getSamResourceName(),
            deleteMe.getResourceId().toString(),
            SamConstants.SamControlledResourceActions.DELETE_ACTION);
    doReturn(false)
        .when(mockSamService)
        .isAuthorized(
            userRequest,
            cannotDeleteMe.getCategory().getSamResourceName(),
            cannotDeleteMe.getResourceId().toString(),
            SamConstants.SamControlledResourceActions.DELETE_ACTION);

    DeleteControlledAzureResourcesStep deleteControlledAzureResourcesStep =
        new DeleteControlledAzureResourcesStep(
            mockResourceDao,
            mockControlledResourceService,
            mockSamService,
            workspaceId,
            userRequest);

    final StepResult result = deleteControlledAzureResourcesStep.doStep(mockFlightContext);

    // Step is expected to fail and no resources should be deleted
    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    verify(mockControlledResourceService, never())
        .deleteControlledResourceSync(any(), any(), any());
    verify(mockControlledResourceService, never())
        .deleteControlledResourceAsync(any(), any(), any(), any(), any());
  }

  @Test
  void testDeletesAllResources() throws InterruptedException, RetryException {
    UUID workspaceId = UUID.randomUUID();
    AuthenticatedUserRequest userRequest =
        new AuthenticatedUserRequest("user@example.com", "subjectId", Optional.empty());
    ControlledResource deleteMe =
        ControlledAzureStorageContainerResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceId)
                    .resourceId(UUID.randomUUID())
                    .name("deleteMe")
                    .cloningInstructions(CloningInstructions.COPY_NOTHING)
                    .createdByEmail(userRequest.getEmail())
                    .managedBy(ManagedByType.MANAGED_BY_USER)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                    .build())
            .storageContainerName("user-storage-container")
            .build();
    ControlledResource deleteMeToo =
        ControlledAzureStorageContainerResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(workspaceId)
                    .resourceId(UUID.randomUUID())
                    .name("deleteMeToo")
                    .cloningInstructions(CloningInstructions.COPY_NOTHING)
                    .createdByEmail("application@example.com")
                    .managedBy(ManagedByType.MANAGED_BY_APPLICATION)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                    .build())
            .storageContainerName("application-storage-container")
            .build();
    doReturn(List.of(deleteMe, deleteMeToo))
        .when(mockResourceDao)
        .listControlledResources(workspaceId, CloudPlatform.AZURE);
    doReturn(true)
        .when(mockSamService)
        .isAuthorized(
            userRequest,
            deleteMe.getCategory().getSamResourceName(),
            deleteMe.getResourceId().toString(),
            SamConstants.SamControlledResourceActions.DELETE_ACTION);
    doReturn(true)
        .when(mockSamService)
        .isAuthorized(
            userRequest,
            deleteMeToo.getCategory().getSamResourceName(),
            deleteMeToo.getResourceId().toString(),
            SamConstants.SamControlledResourceActions.DELETE_ACTION);

    DeleteControlledAzureResourcesStep deleteControlledAzureResourcesStep =
        new DeleteControlledAzureResourcesStep(
            mockResourceDao,
            mockControlledResourceService,
            mockSamService,
            workspaceId,
            userRequest);

    final StepResult result = deleteControlledAzureResourcesStep.doStep(mockFlightContext);

    assertThat(result, equalTo(StepResult.getStepResultSuccess()));
    verify(mockControlledResourceService)
        .deleteControlledResourceSync(workspaceId, deleteMe.getResourceId(), false, userRequest);
    verify(mockControlledResourceService)
        .deleteControlledResourceSync(workspaceId, deleteMeToo.getResourceId(), false, userRequest);
  }
}
