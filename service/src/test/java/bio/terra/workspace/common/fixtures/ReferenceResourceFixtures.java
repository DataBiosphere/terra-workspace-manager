package bio.terra.workspace.common.fixtures;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertMapToApiProperties;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.utils.MockMvcUtils.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.utils.TestUtils.appendRandomNumber;

import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiReferenceResourceCommonFields;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import java.util.Map;
import java.util.UUID;

public class ReferenceResourceFixtures {
  private static final Map<String, String> DEFAULT_RESOURCE_PROPERTIES = Map.of("foo", "bar");

  public static WsmResourceFields.Builder<?> makeDefaultWsmResourceFieldBuilder(UUID workspaceId) {
    return WsmResourceFields.builder()
      .workspaceUuid(workspaceId)
      .resourceId(UUID.randomUUID())
      .name(TestUtils.appendRandomNumber("a-referenced-resource"))
      .description("a description")
      .cloningInstructions(CloningInstructions.COPY_NOTHING)
      .properties(DEFAULT_RESOURCE_PROPERTIES)
      .createdByEmail(DEFAULT_USER_EMAIL);
  }

  public static WsmResourceFields makeDefaultWsmResourceFields(UUID workspaceId) {
    return makeDefaultWsmResourceFieldBuilder(workspaceId).build();
  }

  public static ReferencedDataRepoSnapshotResource makeDataRepoSnapshotResource(
      UUID workspaceUuid) {
    UUID resourceId = UUID.randomUUID();
    String resourceName = "testdatarepo-" + resourceId;
    return new ReferencedDataRepoSnapshotResource(
        makeDefaultWsmResourceFieldBuilder(workspaceUuid)
            .resourceId(resourceId)
            .name(resourceName)
            .build(),
        "terra",
        "polaroid");
  }

  public static ReferencedBigQueryDatasetResource makeReferencedBqDatasetResource(
      UUID workspaceId, String projectId, String bqDataset) {
    UUID resourceId = UUID.randomUUID();
    return new ReferencedBigQueryDatasetResource(
      makeDefaultWsmResourceFieldBuilder(workspaceId)
        .resourceId(resourceId)
        .name("testbq-" + resourceId)
        .build(),
      projectId,
      bqDataset);
  }

  public static ApiReferenceResourceCommonFields makeDefaultReferencedResourceFieldsApi() {
    return new ApiReferenceResourceCommonFields()
        .name(appendRandomNumber("test_resource"))
        .description(RESOURCE_DESCRIPTION)
        .cloningInstructions(ApiCloningInstructionsEnum.NOTHING)
        .properties(convertMapToApiProperties(DEFAULT_RESOURCE_PROPERTIES));
  }
}
