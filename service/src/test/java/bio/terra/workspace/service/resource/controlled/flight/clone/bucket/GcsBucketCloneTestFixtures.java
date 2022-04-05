package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import com.google.cloud.Identity;
import com.google.cloud.Policy;
import com.google.cloud.Role;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GcsBucketCloneTestFixtures {
  public static final UUID SOURCE_WORKSPACE_ID = UUID.randomUUID();
  public static final UUID DESTINATION_WORKSPACE_ID = UUID.randomUUID();
  public static final String SOURCE_PROJECT_ID = "popular-strawberry-1234";
  public static final String DESTINATION_PROJECT_ID = "hairless-kiwi-5678";
  public static final String SOURCE_BUCKET_NAME = "source-bucket";
  public static final String DESTINATION_BUCKET_NAME = "destination-bucket";
  // Stairway ser/des doesn't handle unmodifiable lists
  public static final List<String> SOURCE_ROLE_NAMES = Stream.of(
      "roles/storage.objectViewer",
      "roles/storage.legacyBucketReader").collect(Collectors.toList());
  public static final List<String> DESTINATION_ROLE_NAMES = Stream.of(
      "roles/storage.legacyBucketWriter").collect(Collectors.toList());
  public static final String STORAGE_TRANSFER_SERVICE_SA_EMAIL = "sts@google.biz";
  public static final BucketCloneInputs SOURCE_BUCKET_CLONE_INPUTS =
      new BucketCloneInputs(SOURCE_WORKSPACE_ID, SOURCE_PROJECT_ID, SOURCE_BUCKET_NAME, SOURCE_ROLE_NAMES);
  public static final BucketCloneInputs DESTINATION_BUCKET_CLONE_INPUTS =
      new BucketCloneInputs(DESTINATION_WORKSPACE_ID, DESTINATION_PROJECT_ID, DESTINATION_BUCKET_NAME,
          DESTINATION_ROLE_NAMES);
  public static final Policy SOURCE_BUCKET_POLICY = Policy.newBuilder()
      .addIdentity(Role.of(SOURCE_ROLE_NAMES.get(0)), Identity.serviceAccount(STORAGE_TRANSFER_SERVICE_SA_EMAIL))
      .addIdentity(Role.of(SOURCE_ROLE_NAMES.get(1)), Identity.serviceAccount(STORAGE_TRANSFER_SERVICE_SA_EMAIL))
      .build();
  public static final Policy EMPTY_POLICY = Policy.newBuilder().build();
  public static final Policy DESTINATION_BUCKET_POLICY = Policy.newBuilder()
      .addIdentity(Role.of(DESTINATION_ROLE_NAMES.get(0)), Identity.serviceAccount(STORAGE_TRANSFER_SERVICE_SA_EMAIL))
      .build();
  public static final String CONTROL_PLANE_PROJECT_ID = "terra-control-plane-2468";

}
