package bio.terra.workspace.service.resource;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.app.controller.ResourceApiController;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiControlledResourceMetadata;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotAttributes;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDataTableAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetAttributes;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketAttributes;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceDescription;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// TODO: most assertEquals() assertions are backwards

public class MakeApiResourceDescriptionTest extends BaseSpringBootUnitTest {
  @Autowired ResourceApiController resourceController;

  private UUID workspaceUuid;
  private UUID resourceId;
  private String resourceName;
  private String description;
  private CloningInstructions cloning;

  @BeforeEach
  public void setup() {
    workspaceUuid = UUID.randomUUID();
    resourceId = UUID.randomUUID();
    resourceName = RandomStringUtils.randomAlphabetic(6);
    description = "Description of " + resourceName;
    cloning = CloningInstructions.COPY_REFERENCE;
  }

  @Test
  public void mapReferencedBigQueryDatasetTest() {
    String projectId = RandomStringUtils.randomAlphabetic(12);
    String datasetName = RandomStringUtils.randomAlphabetic(12);

    var resource =
        ReferencedBigQueryDatasetResource.builder()
            .wsmResourceFields(
                ReferenceResourceFixtures.makeDefaultWsmResourceFieldBuilder(workspaceUuid)
                    .resourceId(resourceId)
                    .name(resourceName)
                    .description(description)
                    .cloningInstructions(cloning)
                    .build())
            .projectId(projectId)
            .datasetName(datasetName)
            .build();

    ApiResourceDescription resourceDescription =
        resourceController.makeApiResourceDescription(resource);
    validateWsmResource(resourceDescription);
    ApiResourceAttributesUnion union = resourceDescription.getResourceAttributes();
    ApiGcpBigQueryDatasetAttributes attributes = union.getGcpBqDataset();
    assertThat(attributes, is(notNullValue()));
    assertEquals(attributes.getDatasetId(), datasetName);
    assertEquals(attributes.getProjectId(), projectId);
  }

  @Test
  public void mapReferencedBigQueryDataTableTest() {
    String projectId = RandomStringUtils.randomAlphabetic(12);
    String datasetName = RandomStringUtils.randomAlphabetic(12);
    String datatableName = RandomStringUtils.randomAlphabetic(12);

    var resource =
        ReferencedBigQueryDataTableResource.builder()
            .wsmResourceFields(
                ReferenceResourceFixtures.makeDefaultWsmResourceFieldBuilder(workspaceUuid)
                    .resourceId(resourceId)
                    .name(resourceName)
                    .description(description)
                    .cloningInstructions(cloning)
                    .build())
            .projectId(projectId)
            .datasetId(datasetName)
            .dataTableId(datatableName)
            .build();

    ApiResourceDescription resourceDescription =
        resourceController.makeApiResourceDescription(resource);
    validateWsmResource(resourceDescription);
    ApiResourceAttributesUnion union = resourceDescription.getResourceAttributes();
    ApiGcpBigQueryDataTableAttributes attributes = union.getGcpBqDataTable();
    assertThat(attributes, is(notNullValue()));
    assertEquals(attributes.getDatasetId(), datasetName);
    assertEquals(attributes.getProjectId(), projectId);
    assertEquals(attributes.getDataTableId(), datatableName);
  }

  @Test
  public void mapReferencedDataRepoSnapshotTest() {
    String snapshotId = UUID.randomUUID().toString();
    String instanceName = RandomStringUtils.randomAlphabetic(5);

    var resource =
        ReferencedDataRepoSnapshotResource.builder()
            .wsmResourceFields(
                ReferenceResourceFixtures.makeDefaultWsmResourceFieldBuilder(workspaceUuid)
                    .resourceId(resourceId)
                    .name(resourceName)
                    .description(description)
                    .cloningInstructions(cloning)
                    .build())
            .instanceName(instanceName)
            .snapshotId(snapshotId)
            .build();

    ApiResourceDescription resourceDescription =
        resourceController.makeApiResourceDescription(resource);
    validateWsmResource(resourceDescription);
    ApiResourceAttributesUnion union = resourceDescription.getResourceAttributes();
    ApiDataRepoSnapshotAttributes attributes = union.getGcpDataRepoSnapshot();
    assertThat(attributes, is(notNullValue()));
    assertEquals(attributes.getInstanceName(), instanceName);
    assertEquals(attributes.getSnapshot(), snapshotId);
  }

  @Test
  public void mapReferencedGcsBucketTest() {
    String bucketName = RandomStringUtils.randomAlphabetic(5).toLowerCase();

    var resource =
        ReferencedGcsBucketResource.builder()
            .wsmResourceFields(
                ReferenceResourceFixtures.makeDefaultWsmResourceFieldBuilder(workspaceUuid)
                    .resourceId(resourceId)
                    .name(resourceName)
                    .description(description)
                    .cloningInstructions(cloning)
                    .build())
            .bucketName(bucketName)
            .build();

    ApiResourceDescription resourceDescription =
        resourceController.makeApiResourceDescription(resource);
    validateWsmResource(resourceDescription);
    ApiResourceAttributesUnion union = resourceDescription.getResourceAttributes();
    ApiGcpGcsBucketAttributes attributes = union.getGcpGcsBucket();
    assertThat(attributes, is(notNullValue()));
    assertEquals(attributes.getBucketName(), bucketName);
  }

  public void validateWsmResource(ApiResourceDescription resourceDescription) {
    ApiResourceMetadata metadata = resourceDescription.getMetadata();
    assertEquals(metadata.getWorkspaceId(), workspaceUuid);
    assertEquals(metadata.getResourceId(), resourceId);
    assertEquals(metadata.getName(), resourceName);
    assertEquals(metadata.getDescription(), description);
  }

  @Nested
  class ControlledResource {
    private String assignedUser;
    private AccessScopeType accessScopeType;
    private ManagedByType managedByType;
    private PrivateResourceState privateResourceState;

    @BeforeEach
    public void controlledSetup() {
      assignedUser = RandomStringUtils.random(20, true, false);
      accessScopeType = AccessScopeType.ACCESS_SCOPE_PRIVATE;
      managedByType = ManagedByType.MANAGED_BY_USER;
      privateResourceState = PrivateResourceState.ACTIVE;
    }

    @Test
    public void mapControlledGcsBucketTest() {
      String bucketName = RandomStringUtils.randomAlphabetic(5).toLowerCase();

      var resource =
          ControlledGcsBucketResource.builder()
              .common(
                  ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                      .workspaceUuid(workspaceUuid)
                      .resourceId(resourceId)
                      .name(resourceName)
                      .description(description)
                      .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                      .assignedUser(assignedUser)
                      .privateResourceState(privateResourceState)
                      .accessScope(accessScopeType)
                      .managedBy(managedByType)
                      .build())
              .bucketName(bucketName)
              .build();

      ApiResourceDescription resourceDescription =
          resourceController.makeApiResourceDescription(resource);
      validateControlledResource(resourceDescription);
      ApiResourceAttributesUnion union = resourceDescription.getResourceAttributes();
      ApiGcpGcsBucketAttributes attributes = union.getGcpGcsBucket();
      assertThat(attributes, is(notNullValue()));
      assertEquals(attributes.getBucketName(), bucketName);
    }

    @Test
    public void mapControlledBigQueryDatasetTest() {
      String datasetName = RandomStringUtils.randomAlphabetic(5).toLowerCase();
      String projectId = "my-project-id";

      var resource =
          ControlledBigQueryDatasetResource.builder()
              .common(
                  ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                      .workspaceUuid(workspaceUuid)
                      .resourceId(resourceId)
                      .name(resourceName)
                      .description(description)
                      .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                      .assignedUser(assignedUser)
                      .privateResourceState(privateResourceState)
                      .accessScope(accessScopeType)
                      .managedBy(managedByType)
                      .build())
              .datasetName(datasetName)
              .projectId(projectId)
              .build();

      ApiResourceDescription resourceDescription =
          resourceController.makeApiResourceDescription(resource);
      validateControlledResource(resourceDescription);
      ApiResourceAttributesUnion union = resourceDescription.getResourceAttributes();
      ApiGcpBigQueryDatasetAttributes attributes = union.getGcpBqDataset();
      assertThat(attributes, is(notNullValue()));
      assertEquals(attributes.getDatasetId(), datasetName);
      assertEquals(attributes.getProjectId(), projectId);
    }

    @Test
    public void mapControlledAiNotebookInstanceTest() {
      String instanceId = RandomStringUtils.randomAlphabetic(5).toLowerCase();

      var resource =
          ControlledAiNotebookInstanceResource.builder()
              .common(
                  ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                      .workspaceUuid(workspaceUuid)
                      .resourceId(resourceId)
                      .name(resourceName)
                      .description(description)
                      .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                      .assignedUser(assignedUser)
                      .accessScope(accessScopeType)
                      .managedBy(managedByType)
                      .build())
              .location("us-east1-b")
              .instanceId(instanceId)
              .projectId("my-project-id")
              .build();

      String projectId = "my-project-id";
      ApiResourceDescription resourceDescription =
          resourceController.makeApiResourceDescription(resource);
      validateControlledResource(resourceDescription);
      ApiResourceAttributesUnion union = resourceDescription.getResourceAttributes();
      ApiGcpAiNotebookInstanceAttributes attributes = union.getGcpAiNotebookInstance();
      assertThat(attributes, is(notNullValue()));
      assertEquals("us-east1-b", attributes.getLocation());
      assertEquals(instanceId, attributes.getInstanceId());
      assertEquals(projectId, attributes.getProjectId());
    }

    public void validateControlledResource(ApiResourceDescription resourceDescription) {
      validateWsmResource(resourceDescription);
      ApiResourceMetadata metadata = resourceDescription.getMetadata();
      ApiControlledResourceMetadata common = metadata.getControlledResourceMetadata();
      assertThat(common, is(notNullValue()));
      assertEquals(common.getAccessScope(), accessScopeType.toApiModel());
      assertEquals(common.getManagedBy(), managedByType.toApiModel());
      ApiPrivateResourceUser user = common.getPrivateResourceUser();
      assertThat(user, is(notNullValue()));
      assertEquals(user.getUserName(), assignedUser);
      assertEquals(ApiCloningInstructionsEnum.RESOURCE, metadata.getCloningInstructions());
    }
  }
}
