package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.DESTINATION_BUCKET_CLONE_INPUTS;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.DESTINATION_BUCKET_NAME;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.DESTINATION_BUCKET_POLICY;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.EMPTY_POLICY;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.SOURCE_BUCKET_CLONE_INPUTS;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.SOURCE_BUCKET_NAME;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.SOURCE_BUCKET_POLICY;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.GcsBucketCloneTestFixtures.STORAGE_TRANSFER_SERVICE_SA_EMAIL;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class BucketCloneRolesComponentTest extends BaseUnitTest {

  @Mock private StorageCow mockStorageCow;
  @Mock private CrlService mockCrlService;
  private BucketCloneRolesService bucketCloneRolesComponent;

  @BeforeEach
  public void setup() {
    bucketCloneRolesComponent = new BucketCloneRolesService(mockCrlService);
    doReturn(mockStorageCow).when(mockCrlService).createStorageCow(anyString());
  }

  @Test
  public void testAddBucketRoles() throws InterruptedException {
    // This is deeply white-box testing! The call sequence in bucketCloneRoleService:
    // - getIamPolicy - to construct the target policy; that returns EMPTY
    // - setIamPolicy - to set the target policy; that returns EMPTY so we test the retry
    // - getIamPolicy - to test if the target policy has been achieved. That returns
    // SOURCE_BUCKET_POLICY
    when(mockStorageCow.getIamPolicy(SOURCE_BUCKET_NAME))
        .thenReturn(EMPTY_POLICY, SOURCE_BUCKET_POLICY);
    when(mockStorageCow.setIamPolicy(SOURCE_BUCKET_NAME, SOURCE_BUCKET_POLICY))
        .thenReturn(EMPTY_POLICY);

    bucketCloneRolesComponent.addBucketRoles(
        SOURCE_BUCKET_CLONE_INPUTS, STORAGE_TRANSFER_SERVICE_SA_EMAIL);
    verify(mockStorageCow).setIamPolicy(SOURCE_BUCKET_NAME, SOURCE_BUCKET_POLICY);
  }

  @Test
  public void testRemoveBucketRoles() throws InterruptedException {
    // Remove reverses the returns in Add test: The call sequence in bucketCloneRoleService:
    // - getIamPolicy - to construct the target policy; that returns SOURCE_BUCKET_POLICY
    // - setIamPolicy - to set the target policy; that returns SOURCE_BUCKET_POLICY so we test the
    // retry
    // - getIamPolicy - to test if the target policy has been achieved. That returns EMPTY
    when(mockStorageCow.getIamPolicy(SOURCE_BUCKET_NAME))
        .thenReturn(SOURCE_BUCKET_POLICY, EMPTY_POLICY);
    when(mockStorageCow.setIamPolicy(SOURCE_BUCKET_NAME, EMPTY_POLICY))
        .thenReturn(SOURCE_BUCKET_POLICY);

    bucketCloneRolesComponent.removeBucketRoles(
        SOURCE_BUCKET_CLONE_INPUTS, STORAGE_TRANSFER_SERVICE_SA_EMAIL);
    verify(mockStorageCow).setIamPolicy(SOURCE_BUCKET_NAME, EMPTY_POLICY);
  }

  @Test
  public void testRemoveAllAddedBucketRoles() throws InterruptedException {
    final FlightMap flightMap = new FlightMap();
    flightMap.put(ControlledResourceKeys.SOURCE_CLONE_INPUTS, SOURCE_BUCKET_CLONE_INPUTS);
    flightMap.put(
        ControlledResourceKeys.DESTINATION_STORAGE_TRANSFER_INPUTS,
        DESTINATION_BUCKET_CLONE_INPUTS);
    flightMap.put(
        ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL,
        STORAGE_TRANSFER_SERVICE_SA_EMAIL);

    // For these removes, we do not cause a retry and complete based on the setIamPolicy return
    // See the remove test for more explanation
    when(mockStorageCow.getIamPolicy(SOURCE_BUCKET_NAME)).thenReturn(SOURCE_BUCKET_POLICY);
    when(mockStorageCow.setIamPolicy(SOURCE_BUCKET_NAME, EMPTY_POLICY)).thenReturn(EMPTY_POLICY);
    when(mockStorageCow.getIamPolicy(DESTINATION_BUCKET_NAME))
        .thenReturn(DESTINATION_BUCKET_POLICY);
    when(mockStorageCow.setIamPolicy(DESTINATION_BUCKET_NAME, EMPTY_POLICY))
        .thenReturn(EMPTY_POLICY);

    bucketCloneRolesComponent.removeAllAddedBucketRoles(flightMap);

    verify(mockStorageCow).setIamPolicy(SOURCE_BUCKET_NAME, EMPTY_POLICY);
    verify(mockStorageCow).setIamPolicy(DESTINATION_BUCKET_NAME, EMPTY_POLICY);
  }
}
