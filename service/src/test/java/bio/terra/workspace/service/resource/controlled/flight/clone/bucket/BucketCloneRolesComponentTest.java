package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.cloud.Identity;
import com.google.cloud.Policy;
import com.google.cloud.Role;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class BucketCloneRolesComponentTest extends BaseUnitTest {

  private static final UUID SOURCE_WORKSPACE_ID = UUID.randomUUID();
  private static final UUID DESTINATION_WORKSPACE_ID = UUID.randomUUID();
  private static final String SOURCE_PROJECT_ID = "popular-strawberry-1234";
  private static final String DESTINATION_PROJECT_ID = "hairless-kiwi-5678";
  private static final String SOURCE_BUCKET_NAME = "source-bucket";
  private static final String DESTINATION_BUCKET_NAME = "destination-bucket";
  // Stairway ser/des doesn't handle unmodifiable lists
  private static final List<String> SOURCE_ROLE_NAMES = Stream.of(
      "roles/storage.objectViewer",
      "roles/storage.legacyBucketReader").collect(Collectors.toList());
  private static final List<String> DESTINATION_ROLE_NAMES = Stream.of(
      "roles/storage.legacyBucketWriter").collect(Collectors.toList());
  private static final String STORAGE_TRANSFER_SERVICE_SA_EMAIL = "sts@google.biz";
  private static final BucketCloneInputs SOURCE_BUCKET_CLONE_INPUTS =
      new BucketCloneInputs(SOURCE_WORKSPACE_ID, SOURCE_PROJECT_ID, SOURCE_BUCKET_NAME, SOURCE_ROLE_NAMES);
  private static final BucketCloneInputs DESTINATION_BUCKET_CLONE_INPUTS =
      new BucketCloneInputs(DESTINATION_WORKSPACE_ID, DESTINATION_PROJECT_ID, DESTINATION_BUCKET_NAME,
          DESTINATION_ROLE_NAMES);
  private static final Policy SOURCE_BUCKET_POLICY = Policy.newBuilder()
      .addIdentity(Role.of(SOURCE_ROLE_NAMES.get(0)), Identity.serviceAccount(STORAGE_TRANSFER_SERVICE_SA_EMAIL))
      .addIdentity(Role.of(SOURCE_ROLE_NAMES.get(1)), Identity.serviceAccount(STORAGE_TRANSFER_SERVICE_SA_EMAIL))
      .build();
  public static final Policy EMPTY_POLICY = Policy.newBuilder().build();
  public static final Policy DESTINATION_BUCKET_POLICY = Policy.newBuilder()
      .addIdentity(Role.of(DESTINATION_ROLE_NAMES.get(0)), Identity.serviceAccount(STORAGE_TRANSFER_SERVICE_SA_EMAIL))
      .build();

  @Mock
  private StorageCow mockStorageCow;
  @Mock private CrlService mockCrlService;
  private BucketCloneRolesComponent bucketCloneRolesComponent;

  @BeforeEach
  public void setup() {
    bucketCloneRolesComponent = new BucketCloneRolesComponent(mockCrlService);
    doReturn(mockStorageCow).when(mockCrlService).createStorageCow(anyString());
  }

  @Test
  public void testAddBucketRoles() {
    doReturn(EMPTY_POLICY).when(mockStorageCow).getIamPolicy(SOURCE_BUCKET_NAME);
    bucketCloneRolesComponent.addBucketRoles(SOURCE_BUCKET_CLONE_INPUTS, STORAGE_TRANSFER_SERVICE_SA_EMAIL);
    verify(mockStorageCow).setIamPolicy(SOURCE_BUCKET_NAME, SOURCE_BUCKET_POLICY);
  }

  @Test
  public void testRemoveBucketRoles() {
    doReturn(SOURCE_BUCKET_POLICY).when(mockStorageCow).getIamPolicy(SOURCE_BUCKET_NAME);
    bucketCloneRolesComponent.removeBucketRoles(SOURCE_BUCKET_CLONE_INPUTS, STORAGE_TRANSFER_SERVICE_SA_EMAIL);
    verify(mockStorageCow).setIamPolicy(SOURCE_BUCKET_NAME, EMPTY_POLICY);
  }

  @Test
  public void testRemoveAllAddedBucketRoles() {
    final FlightMap flightMap = new FlightMap();
    flightMap.put(ControlledResourceKeys.SOURCE_CLONE_INPUTS, SOURCE_BUCKET_CLONE_INPUTS);
    flightMap.put(ControlledResourceKeys.DESTINATION_CLONE_INPUTS, DESTINATION_BUCKET_CLONE_INPUTS);
    flightMap.put(ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL, STORAGE_TRANSFER_SERVICE_SA_EMAIL);

    doReturn(SOURCE_BUCKET_POLICY).when(mockStorageCow).getIamPolicy(SOURCE_BUCKET_NAME);
    doReturn(DESTINATION_BUCKET_POLICY).when(mockStorageCow).getIamPolicy(DESTINATION_BUCKET_NAME);

    bucketCloneRolesComponent.removeAllAddedBucketRoles(flightMap);

    verify(mockStorageCow).setIamPolicy(SOURCE_BUCKET_NAME, EMPTY_POLICY);
    verify(mockStorageCow).setIamPolicy(DESTINATION_BUCKET_NAME, EMPTY_POLICY);
  }
}
