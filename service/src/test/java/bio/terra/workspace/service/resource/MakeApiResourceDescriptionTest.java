package bio.terra.workspace.service.resource;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.app.controller.ResourceController;
import bio.terra.workspace.common.BaseUnitTest;
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
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class MakeApiResourceDescriptionTest extends BaseUnitTest {
  @Autowired ResourceController resourceController;

  private UUID workspaceId;
  private UUID resourceId;
  private String resourceName;
  private String description;
  private CloningInstructions cloning;

  @BeforeEach
  public void setup() {
    workspaceId = UUID.randomUUID();
    resourceId = UUID.randomUUID();
    resourceName = RandomStringUtils.randomAlphabetic(6);
    description = "Description of " + resourceName;
    cloning = CloningInstructions.COPY_DEFINITION;
  }

  @Test
  public void mapReferencedBigQueryDatasetTest() throws Exception {
    String projectId = RandomStringUtils.randomAlphabetic(12);
    String datasetName = RandomStringUtils.randomAlphabetic(12);

    var resource =
        new ReferencedBigQueryDatasetResource(
            workspaceId, resourceId, resourceName, description, cloning, projectId, datasetName);

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
  public void mapReferencedBigQueryDataTableTest() throws Exception {
    String projectId = RandomStringUtils.randomAlphabetic(12);
    String datasetName = RandomStringUtils.randomAlphabetic(12);
    String datatableName = RandomStringUtils.randomAlphabetic(12);

    var resource =
        new ReferencedBigQueryDataTableResource(
            workspaceId,
            resourceId,
            resourceName,
            description,
            cloning,
            projectId,
            datasetName,
            datatableName);

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
  public void mapReferencedDataRepoSnapshotTest() throws Exception {
    String snapshotId = UUID.randomUUID().toString();
    String instanceName = RandomStringUtils.randomAlphabetic(5);

    var resource =
        new ReferencedDataRepoSnapshotResource(
            workspaceId, resourceId, resourceName, description, cloning, instanceName, snapshotId);

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
  public void mapReferencedGcsBucketTest() throws Exception {
    String bucketName = RandomStringUtils.randomAlphabetic(5).toLowerCase();

    var resource =
        new ReferencedGcsBucketResource(
            workspaceId, resourceId, resourceName, description, cloning, bucketName);

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
    assertEquals(metadata.getWorkspaceId(), workspaceId);
    assertEquals(metadata.getResourceId(), resourceId);
    assertEquals(metadata.getName(), resourceName);
    assertEquals(metadata.getDescription(), description);
    assertEquals(metadata.getCloningInstructions(), cloning.toApiModel());
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
    public void mapControlledGcsBucketTest() throws Exception {
      String bucketName = RandomStringUtils.randomAlphabetic(5).toLowerCase();

      var resource =
          new ControlledGcsBucketResource(
              workspaceId,
              resourceId,
              resourceName,
              description,
              cloning,
              assignedUser,
              privateResourceState,
              accessScopeType,
              managedByType,
              null,
              bucketName);

      ApiResourceDescription resourceDescription =
          resourceController.makeApiResourceDescription(resource);
      validateControlledResource(resourceDescription);
      ApiResourceAttributesUnion union = resourceDescription.getResourceAttributes();
      ApiGcpGcsBucketAttributes attributes = union.getGcpGcsBucket();
      assertThat(attributes, is(notNullValue()));
      assertEquals(attributes.getBucketName(), bucketName);
    }

    @Test
    public void mapControlledBigQueryDatasetTest() throws Exception {
      String datasetName = RandomStringUtils.randomAlphabetic(5).toLowerCase();
      String projectId = "my-project-id";

      var resource =
          new ControlledBigQueryDatasetResource(
              workspaceId,
              resourceId,
              resourceName,
              description,
              cloning,
              assignedUser,
              privateResourceState,
              accessScopeType,
              managedByType,
              null,
              datasetName,
              projectId);

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
    public void mapControlledAiNotebookInstanceTest() throws Exception {
      String instanceId = RandomStringUtils.randomAlphabetic(5).toLowerCase();

      var resource =
          ControlledAiNotebookInstanceResource.builder()
              .workspaceId(workspaceId)
              .resourceId(resourceId)
              .name(resourceName)
              .description(description)
              .cloningInstructions(cloning)
              .assignedUser(assignedUser)
              .accessScope(accessScopeType)
              .managedBy(managedByType)
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
    }
  }
}
