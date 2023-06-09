package bio.terra.workspace.service.workspace.flight.aws;

import static bio.terra.workspace.common.testfixtures.ControlledResourceFixtures.WORKSPACE_ID;
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
import bio.terra.workspace.common.BaseAwsUnitTest;
import bio.terra.workspace.common.testfixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.testutils.MockMvcUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder.ControlledAwsS3StorageFolderResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.cloud.aws.DeleteControlledAwsResourcesStep;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class DeleteControlledAwsResourcesStepTest extends BaseAwsUnitTest {

  @Mock private ControlledResourceService mockControlledResourceService;
  @Mock private ResourceDao mockResourceDao;
  @Mock private SamService mockSamService;
  @Mock private FlightContext mockFlightContext;

  @Test
  void deleteOnlyWithAuthTest() throws InterruptedException, RetryException {
    ControlledResource canDelete1 =
        ControlledAwsResourceFixtures.makeAwsS3StorageFolderResourceBuilder(
                WORKSPACE_ID, "can-delete-1", "bucket-name", "prefix-can-delete-1")
            .build();
    ControlledResource canDelete2 =
        ControlledAwsResourceFixtures.makeAwsS3StorageFolderResourceBuilder(
                WORKSPACE_ID, "can-delete-2", "bucket-name", "prefix-can-delete-2")
            .build();
    ControlledResource cannotDelete =
        ControlledAwsS3StorageFolderResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(WORKSPACE_ID)
                    .resourceId(UUID.randomUUID())
                    .name("cannot-delete")
                    .cloningInstructions(CloningInstructions.COPY_NOTHING)
                    .createdByEmail("application@example.com")
                    .managedBy(ManagedByType.MANAGED_BY_APPLICATION)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                    .region("us-east-1")
                    .build())
            .bucketName("bucket-name")
            .prefix("prefix-cannot-delete")
            .build();

    AuthenticatedUserRequest userRequest = MockMvcUtils.USER_REQUEST;
    doReturn(true)
        .when(mockSamService)
        .isAuthorized(
            userRequest,
            canDelete1.getCategory().getSamResourceName(),
            canDelete1.getResourceId().toString(),
            SamConstants.SamControlledResourceActions.DELETE_ACTION);

    doReturn(true)
        .when(mockSamService)
        .isAuthorized(
            userRequest,
            canDelete2.getCategory().getSamResourceName(),
            canDelete2.getResourceId().toString(),
            SamConstants.SamControlledResourceActions.DELETE_ACTION);

    doReturn(false)
        .when(mockSamService)
        .isAuthorized(
            userRequest,
            cannotDelete.getCategory().getSamResourceName(),
            cannotDelete.getResourceId().toString(),
            SamConstants.SamControlledResourceActions.DELETE_ACTION);

    DeleteControlledAwsResourcesStep deleteControlledAwsResourcesStep =
        new DeleteControlledAwsResourcesStep(
            mockResourceDao,
            mockControlledResourceService,
            mockSamService,
            WORKSPACE_ID,
            userRequest);

    // Step is expected to fail and no resources should be deleted if at least one resource cannot
    // be deleted
    doReturn(List.of(canDelete1, canDelete2, cannotDelete))
        .when(mockResourceDao)
        .listControlledResources(WORKSPACE_ID, CloudPlatform.AWS);

    StepResult result = deleteControlledAwsResourcesStep.doStep(mockFlightContext);
    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    verify(mockControlledResourceService, never())
        .deleteControlledResourceSync(any(), any(), any());
    verify(mockControlledResourceService, never())
        .deleteControlledResourceAsync(any(), any(), any(), any(), any());

    // Step is expected to success and all resources should be deleted only if all resources can be
    // deleted
    doReturn(List.of(canDelete1, canDelete2))
        .when(mockResourceDao)
        .listControlledResources(WORKSPACE_ID, CloudPlatform.AWS);
    result = deleteControlledAwsResourcesStep.doStep(mockFlightContext);
    assertThat(result.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
    verify(mockControlledResourceService)
        .deleteControlledResourceSync(WORKSPACE_ID, canDelete1.getResourceId(), true, userRequest);
    verify(mockControlledResourceService)
        .deleteControlledResourceSync(WORKSPACE_ID, canDelete2.getResourceId(), true, userRequest);
  }
}
