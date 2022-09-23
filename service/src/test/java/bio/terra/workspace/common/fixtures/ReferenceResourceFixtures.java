package bio.terra.workspace.common.fixtures;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertMapToApiProperties;
import static bio.terra.workspace.common.utils.TestUtils.appendRandomNumber;

import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpGcsBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpGcsObjectReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGitRepoReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDataTableAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetAttributes;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketAttributes;
import bio.terra.workspace.generated.model.ApiGcpGcsObjectAttributes;
import bio.terra.workspace.generated.model.ApiGitRepoAttributes;
import bio.terra.workspace.generated.model.ApiReferenceResourceCommonFields;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo.ReferencedGitRepoResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;

public class ReferenceResourceFixtures {
  private static final Map<String, String> DEFAULT_RESOURCE_PROPERTIES = Map.of("foo", "bar");

  public static WsmResourceFields.Builder makeDefaultWsmResourceFieldBuilder(UUID workspaceId) {
    return WsmResourceFields.builder()
        .workspaceUuid(workspaceId)
        .resourceId(UUID.randomUUID())
        .name(TestUtils.appendRandomNumber("a-referenced-resource"))
        .cloningInstructions(CloningInstructions.COPY_NOTHING)
        .properties(DEFAULT_RESOURCE_PROPERTIES);
  }

  public static ReferencedGcsBucketResource makeReferencedGcsBucketResource(UUID workspaceId) {
    return ReferencedGcsBucketResource.builder()
        .bucketName(appendRandomNumber("bucket"))
        .wsmResourceFields(makeDefaultWsmResourceFieldBuilder(workspaceId).build()).build();
  }

  public static ReferencedBigQueryDataTableResource makeReferencedBigQueryDataTableResource(UUID workspaceId) {
    return ReferencedBigQueryDataTableResource.builder()
        .datasetId(appendRandomNumber("dataset"))
        .dataTableId(appendRandomNumber("datatable"))
        .projectId("my-gcp-project").build();
  }

  public static ReferencedGitRepoResource makeReferencedGitRepoResource(UUID workspaceId) {
    return ReferencedGitRepoResource.builder()
        .gitRepoUrl("ssh://github.com:foo/my-awesome-repo.git").build();
  }

  public static ReferencedDataRepoSnapshotResource makeDataRepoSnapshotResource(
      UUID workspaceUuid) {
    UUID resourceId = UUID.randomUUID();
    String resourceName = "testdatarepo-" + resourceId;

    return new ReferencedDataRepoSnapshotResource(
        workspaceUuid,
        resourceId,
        resourceName,
        "description of " + resourceName,
        CloningInstructions.COPY_NOTHING,
        "terra",
        "polaroid",
        /*resourceLineage=*/ null,
        /*properties*/ DEFAULT_RESOURCE_PROPERTIES);
  }

  public static ApiCreateDataRepoSnapshotReferenceRequestBody
      makeDataRepoSnapshotReferenceRequestBody() {
    return new ApiCreateDataRepoSnapshotReferenceRequestBody()
        .snapshot(
            new ApiDataRepoSnapshotAttributes()
                .snapshot("This is a snapshot")
                .instanceName(RandomStringUtils.randomAlphabetic(10)))
        .metadata(makeDefaultReferencedResourceFieldsApi());
  }

  public static ApiCreateGcpGcsBucketReferenceRequestBody makeGcsBucketReferenceRequestBody() {
    return new ApiCreateGcpGcsBucketReferenceRequestBody()
        .bucket(
            new ApiGcpGcsBucketAttributes()
                .bucketName(RandomStringUtils.randomAlphabetic(10).toLowerCase()))
        .metadata(makeDefaultReferencedResourceFieldsApi());
  }

  public static ApiCreateGcpGcsObjectReferenceRequestBody makeGcsObjectReferenceRequestBody() {
    return new ApiCreateGcpGcsObjectReferenceRequestBody()
        .file(
            new ApiGcpGcsObjectAttributes()
                .bucketName(appendRandomNumber("gcsbucket"))
                .fileName("foo/bar"))
        .metadata(makeDefaultReferencedResourceFieldsApi());
  }

  public static ApiCreateGcpBigQueryDatasetReferenceRequestBody
      makeGcpBqDatasetReferenceRequestBody() {
    return new ApiCreateGcpBigQueryDatasetReferenceRequestBody()
        .dataset(
            new ApiGcpBigQueryDatasetAttributes()
                .datasetId(appendRandomNumber("dataset").replace("-", "_"))
                .projectId(appendRandomNumber("my-gcp-project")))
        .metadata(makeDefaultReferencedResourceFieldsApi());
  }

  public static ApiCreateGcpBigQueryDataTableReferenceRequestBody
      makeBqDataTableReferenceRequestBody() {
    return new ApiCreateGcpBigQueryDataTableReferenceRequestBody()
        .dataTable(
            new ApiGcpBigQueryDataTableAttributes()
                .dataTableId(appendRandomNumber("datatable"))
                .datasetId(appendRandomNumber("dataset").replace("-", "_"))
                .projectId(appendRandomNumber("my-project-id")))
        .metadata(makeDefaultReferencedResourceFieldsApi());
  }

  public static ApiCreateGitRepoReferenceRequestBody makeGitRepoReferenceRequestBody() {
    return new ApiCreateGitRepoReferenceRequestBody()
        .gitrepo(new ApiGitRepoAttributes().gitRepoUrl("git@github.com:foo/bar"))
        .metadata(makeDefaultReferencedResourceFieldsApi());
  }

  public static ApiReferenceResourceCommonFields makeDefaultReferencedResourceFieldsApi() {
    return new ApiReferenceResourceCommonFields()
        .name(appendRandomNumber("test_resource").replace("-", "_"))
        .description("This is a referenced resource")
        .cloningInstructions(ApiCloningInstructionsEnum.NOTHING)
        .properties(convertMapToApiProperties(DEFAULT_RESOURCE_PROPERTIES));
  }
}
