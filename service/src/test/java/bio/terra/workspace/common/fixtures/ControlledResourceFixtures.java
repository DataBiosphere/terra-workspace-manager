package bio.terra.workspace.common.fixtures;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertMapToApiProperties;

import bio.terra.stairway.ShortUUID;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiControlledFlexibleResourceCreationParameters;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.generated.model.ApiFlexibleResourceUpdateParameters;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.google.api.client.util.DateTime;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.lang3.RandomStringUtils;

/** A series of static objects useful for testing controlled resources. */
public class ControlledResourceFixtures {

  public static final UUID WORKSPACE_ID = UUID.fromString("00000000-fcf0-4981-bb96-6b8dd634e7c0");
  public static final UUID RESOURCE_ID = UUID.fromString("11111111-fcf0-4981-bb96-6b8dd634e7c0");
  public static final String OWNER_EMAIL = "jay@all-the-bits-thats-fit-to-blit.dev";
  public static final String BUCKET_NAME_PREFIX = "my-bucket";
  public static final Map<String, String> DEFAULT_RESOURCE_PROPERTIES = Map.of("foo", "bar");

  private ControlledResourceFixtures() {}

  public static String uniqueBucketName() {
    return TestUtils.appendRandomNumber(BUCKET_NAME_PREFIX);
  }

  public static String uniqueStorageAccountName() {
    return UUID.randomUUID().toString().toLowerCase().replace("-", "").substring(0, 23);
  }

  public static ApiControlledFlexibleResourceCreationParameters
  defaultFlexResourceCreationParameters() {
    return new ApiControlledFlexibleResourceCreationParameters()
        .typeNamespace("terra")
        .type("fake-flexible-type")
        .data(null);
  }

  public static final byte[] DEFAULT_UPDATE_FLEX_DATA =
      "{\"description\":\"this is new JSON\"}".getBytes(StandardCharsets.UTF_8);

  public static ApiFlexibleResourceUpdateParameters defaultFlexUpdateParameters() {
    return new ApiFlexibleResourceUpdateParameters().data(DEFAULT_UPDATE_FLEX_DATA);
  }

  public static final String RESOURCE_NAME = "my_first_bucket";

  public static final String RESOURCE_DESCRIPTION =
      "A bucket that had beer in it, briefly. \uD83C\uDF7B";
  public static final CloningInstructions CLONING_INSTRUCTIONS = CloningInstructions.COPY_RESOURCE;
  public static final String DEFAULT_RESOURCE_REGION = "us-central1";

  public static final Long DEFAULT_CREATED_BIG_QUERY_TABLE_LIFETIME = 5900L;
  public static final Long DEFAULT_CREATED_BIG_QUERY_PARTITION_LIFETIME = 5901L;


  /** Returns a {@link ControlledResourceFields.Builder} with the fields filled in */
  public static ControlledResourceFields.Builder makeDefaultControlledResourceFieldsBuilder() {
    return ControlledResourceFields.builder()
        .workspaceUuid(UUID.randomUUID())
        .resourceId(UUID.randomUUID())
        .name(RandomStringUtils.randomAlphabetic(10))
        .description("how much data could a dataset set if a dataset could set data?")
        .description(RESOURCE_DESCRIPTION)
        .cloningInstructions(CloningInstructions.COPY_DEFINITION)
        .assignedUser(null)
        .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
        .managedBy(ManagedByType.MANAGED_BY_USER)
        .privateResourceState(PrivateResourceState.NOT_APPLICABLE)
        .properties(DEFAULT_RESOURCE_PROPERTIES)
        .createdByEmail(MockMvcUtils.DEFAULT_USER_EMAIL)
        .region(DEFAULT_RESOURCE_REGION);
  }

  /**
   * Returns a {@link ControlledResourceFields} that is ready to be included in a controlled
   * resource builder.
   */
  public static ControlledResourceFields makeDefaultControlledResourceFields(
      @Nullable UUID inWorkspaceId) {
    return makeControlledResourceFieldsBuilder(inWorkspaceId).build();
  }

  /**
   * Returns a {@link ControlledResourceFields.Builder} with default values. This builder can be
   * modified for particular fields before being included in a controlled resource builder.
   */
  public static ControlledResourceFields.Builder makeControlledResourceFieldsBuilder(
      @Nullable UUID inWorkspaceId) {
    ControlledResourceFields.Builder builder = makeDefaultControlledResourceFieldsBuilder();
    if (inWorkspaceId != null) {
      builder.workspaceUuid(inWorkspaceId);
    }
    return builder;
  }

  /**
   * Returns the same fields as {@link #makeDefaultControlledResourceFields(UUID)}, but in the
   * format required for a controller API call.
   */
  public static ApiControlledResourceCommonFields makeDefaultControlledResourceFieldsApi() {
    ControlledResourceFields commonFields = makeDefaultControlledResourceFieldsBuilder().build();
    return new ApiControlledResourceCommonFields()
        .name(commonFields.getName())
        .description(commonFields.getDescription())
        .cloningInstructions(commonFields.getCloningInstructions().toApiModel())
        .accessScope(commonFields.getAccessScope().toApiModel())
        .managedBy(commonFields.getManagedBy().toApiModel())
        .resourceId(commonFields.getResourceId())
        .properties(convertMapToApiProperties(commonFields.getProperties()));
  }

  /** Make a flex resource builder with defaults filled in. */
  public static ControlledFlexibleResource.Builder makeDefaultFlexResourceBuilder(
      @Nullable UUID workspaceUuid) {
    return new ControlledFlexibleResource.Builder()
        .common(makeDefaultControlledResourceFields(workspaceUuid))
        .typeNamespace("terra")
        .type("fake-flexible-type")
        .data(null);
  }

  public static String uniqueDatasetId() {
    return "my_test_dataset_" + ShortUUID.get().replace("-", "_");
  }

  /**
   * Returns a {@link ControlledResourceFields.Builder} that is ready to be built.
   *
   * <p>Tests should not rely on any particular value for the fields returned by this function and
   * instead override the values that they care about.
   */
  public static ControlledResourceFields.Builder makeNotebookCommonFieldsBuilder() {
    return ControlledResourceFields.builder()
        .workspaceUuid(UUID.randomUUID())
        .resourceId(UUID.randomUUID())
        .name(TestUtils.appendRandomNumber("my-instance"))
        .description("my notebook description")
        .cloningInstructions(CloningInstructions.COPY_NOTHING)
        .assignedUser("myusername@mydomain.mine")
        .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
        .managedBy(ManagedByType.MANAGED_BY_USER)
        .createdByEmail(MockMvcUtils.DEFAULT_USER_EMAIL)
        .region(DEFAULT_RESOURCE_REGION);
  }

  public static final OffsetDateTime OFFSET_DATE_TIME_1 =
      OffsetDateTime.parse("2017-12-03T10:15:30+01:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  public static final OffsetDateTime OFFSET_DATE_TIME_2 =
      OffsetDateTime.parse("2017-12-03T10:15:30-05:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);

  public static final DateTime DATE_TIME_1 = DateTime.parseRfc3339("1985-04-12T23:20:50.52Z");
  public static final DateTime DATE_TIME_2 = DateTime.parseRfc3339("1996-12-19T16:39:57-08:00");

  public static void insertControlledResourceRow(
      ResourceDao resourceDao, ControlledResource resource) {
    String fakeFlightId = UUID.randomUUID().toString();
    resourceDao.createResourceStart(resource, fakeFlightId);
    resourceDao.createResourceSuccess(resource, fakeFlightId);
  }
}
