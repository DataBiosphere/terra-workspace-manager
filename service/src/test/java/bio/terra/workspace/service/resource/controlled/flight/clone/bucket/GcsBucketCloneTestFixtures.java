package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycle;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRule;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleActionType;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
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
  public static final String SOURCE_BUCKET_DESCRIPTION = "A bucket with a hole in it.";
  public static final String DESTINATION_BUCKET_NAME = "destination-bucket";
  public static final String SOURCE_RESOURCE_NAME = "source_bucket";
  public static final ApiGcpGcsBucketCreationParameters SOURCE_BUCKET_CREATION_PARAMETERS =
      new ApiGcpGcsBucketCreationParameters()
          .defaultStorageClass(ApiGcpGcsBucketDefaultStorageClass.NEARLINE)
          .name(SOURCE_RESOURCE_NAME)
          .lifecycle(
              new ApiGcpGcsBucketLifecycle()
                  .addRulesItem(
                      new ApiGcpGcsBucketLifecycleRule()
                          .condition(new ApiGcpGcsBucketLifecycleRuleCondition().age(30))
                          .action(
                              new ApiGcpGcsBucketLifecycleRuleAction()
                                  .type(ApiGcpGcsBucketLifecycleRuleActionType.DELETE))))
          .location("us-west1");
  public static final ControlledGcsBucketResource SOURCE_BUCKET_RESOURCE =
      ControlledGcsBucketResource.builder()
          .common(
              ControlledResourceFields.builder()
                  .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                  .applicationId(null)
                  .assignedUser(null)
                  .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                  .description(null)
                  .iamRole(ControlledResourceIamRole.OWNER)
                  .managedBy(ManagedByType.MANAGED_BY_USER)
                  .name(SOURCE_RESOURCE_NAME)
                  .privateResourceState(null)
                  .resourceId(UUID.randomUUID())
                  .workspaceUuid(SOURCE_WORKSPACE_ID)
                  .build())
          .bucketName(SOURCE_BUCKET_NAME)
          .build();
  public static final ControlledGcsBucketResource CREATED_BUCKET_RESOURCE =
      ControlledGcsBucketResource.builder()
          .common(
              ControlledResourceFields.builder()
                  .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                  .applicationId(null)
                  .assignedUser(null)
                  .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                  .description(null)
                  .iamRole(ControlledResourceIamRole.OWNER)
                  .managedBy(ManagedByType.MANAGED_BY_USER)
                  .name("clone_of_source_bucket")
                  .privateResourceState(null)
                  .resourceId(UUID.randomUUID())
                  .workspaceUuid(DESTINATION_WORKSPACE_ID)
                  .build())
          .bucketName(DESTINATION_BUCKET_NAME)
          .build();
  // Stairway ser/des doesn't handle unmodifiable lists
  public static final List<String> SOURCE_ROLE_NAMES =
      Stream.of("roles/storage.objectViewer", "roles/storage.legacyBucketReader")
          .collect(Collectors.toList());
  public static final List<String> DESTINATION_ROLE_NAMES =
      Stream.of("roles/storage.legacyBucketWriter").collect(Collectors.toList());
  public static final String STORAGE_TRANSFER_SERVICE_SA_EMAIL = "sts@google.biz";
  public static final BucketCloneInputs SOURCE_BUCKET_CLONE_INPUTS =
      new BucketCloneInputs(
          SOURCE_WORKSPACE_ID, SOURCE_PROJECT_ID, SOURCE_BUCKET_NAME, SOURCE_ROLE_NAMES);
  public static final BucketCloneInputs DESTINATION_BUCKET_CLONE_INPUTS =
      new BucketCloneInputs(
          DESTINATION_WORKSPACE_ID,
          DESTINATION_PROJECT_ID,
          DESTINATION_BUCKET_NAME,
          DESTINATION_ROLE_NAMES);
  public static final Policy SOURCE_BUCKET_POLICY =
      Policy.newBuilder()
          .addIdentity(
              Role.of(SOURCE_ROLE_NAMES.get(0)),
              Identity.serviceAccount(STORAGE_TRANSFER_SERVICE_SA_EMAIL))
          .addIdentity(
              Role.of(SOURCE_ROLE_NAMES.get(1)),
              Identity.serviceAccount(STORAGE_TRANSFER_SERVICE_SA_EMAIL))
          .build();
  public static final Policy EMPTY_POLICY = Policy.newBuilder().build();
  public static final Policy DESTINATION_BUCKET_POLICY =
      Policy.newBuilder()
          .addIdentity(
              Role.of(DESTINATION_ROLE_NAMES.get(0)),
              Identity.serviceAccount(STORAGE_TRANSFER_SERVICE_SA_EMAIL))
          .build();
  public static final String CONTROL_PLANE_PROJECT_ID = "terra-control-plane-2468";
}
