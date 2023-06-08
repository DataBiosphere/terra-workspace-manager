package bio.terra.workspace.common.testfixtures;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertMapToApiProperties;
import static bio.terra.workspace.common.testfixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.testutils.MockMvcUtils.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.testutils.TestUtils.appendRandomNumber;

import bio.terra.workspace.common.testutils.TestUtils;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiReferenceResourceCommonFields;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo.ReferencedGitRepoResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsobject.ReferencedGcsObjectResource;
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
        UUID.randomUUID().toString());
  }

  public static ReferencedGitRepoResource makeGitRepoResource(
      UUID workspaceUuid, String gitRepoUrl) {
    UUID resourceId = UUID.randomUUID();
    String resourceName = "testgitrepo-" + resourceId;
    return new ReferencedGitRepoResource(
        makeDefaultWsmResourceFieldBuilder(workspaceUuid)
            .resourceId(resourceId)
            .name(resourceName)
            .build(),
        gitRepoUrl);
  }

  public static ReferencedBigQueryDatasetResource makeReferencedBqDatasetResource(
      UUID workspaceId, String projectId, String bqDataset) {
    UUID resourceId = UUID.randomUUID();
    return new ReferencedBigQueryDatasetResource(
        makeDefaultWsmResourceFieldBuilder(workspaceId)
            .resourceId(resourceId)
            .name(TestUtils.appendRandomNumber("testbq_"))
            .build(),
        projectId,
        bqDataset);
  }

  public static ReferencedBigQueryDataTableResource makeReferencedBqDataTableResource(
      UUID workspaceId, String projectId, String bqDataset, String file) {
    UUID resourceId = UUID.randomUUID();

    return new ReferencedBigQueryDataTableResource(
        makeDefaultWsmResourceFieldBuilder(workspaceId)
            .resourceId(resourceId)
            .name(TestUtils.appendRandomNumber("testbq_"))
            .build(),
        projectId,
        bqDataset,
        file);
  }

  public static ReferencedGcsBucketResource makeReferencedGcsBucketResource(
      UUID workspaceId, String bucketName) {
    UUID resourceId = UUID.randomUUID();
    return new ReferencedGcsBucketResource(
        makeDefaultWsmResourceFieldBuilder(workspaceId)
            .resourceId(resourceId)
            .name(TestUtils.appendRandomNumber("testgcs"))
            .build(),
        bucketName);
  }

  public static ReferencedGcsObjectResource makeReferencedGcsObjectResource(
      UUID workspaceId, String bucketName, String file) {
    UUID resourceId = UUID.randomUUID();
    return new ReferencedGcsObjectResource(
        makeDefaultWsmResourceFieldBuilder(workspaceId)
            .resourceId(resourceId)
            .name(TestUtils.appendRandomNumber("testgcs"))
            .build(),
        bucketName,
        file);
  }

  public static ApiReferenceResourceCommonFields makeDefaultReferencedResourceFieldsApi() {
    return new ApiReferenceResourceCommonFields()
        .name(appendRandomNumber("test_resource"))
        .description(RESOURCE_DESCRIPTION)
        .cloningInstructions(ApiCloningInstructionsEnum.NOTHING)
        .properties(convertMapToApiProperties(DEFAULT_RESOURCE_PROPERTIES));
  }
}
