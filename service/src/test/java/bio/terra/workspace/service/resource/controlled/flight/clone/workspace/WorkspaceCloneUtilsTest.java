package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.WsmResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

public class WorkspaceCloneUtilsTest extends BaseUnitTest {
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID DESTINATION_WORKSPACE_ID = UUID.randomUUID();
  private static final UUID DESTINATION_RESOURCE_ID = UUID.randomUUID();

  @Test
  public void buildDestinationControlledBigQueryDataset() {
    var sourceDataset =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(WORKSPACE_ID).build();
    var cloneResourceName = RandomStringUtils.randomAlphabetic(5);
    var cloneDescription = "This is a cloned dataset";
    var cloneDatasetName = RandomStringUtils.randomAlphabetic(5);
    var cloneProjectName = "my-cloned-gcp-project";

    var datasetToClone =
        (ControlledBigQueryDatasetResource)
            WorkspaceCloneUtils.buildDestinationControlledBigQueryDataset(
                sourceDataset,
                DESTINATION_WORKSPACE_ID,
                DESTINATION_RESOURCE_ID,
                cloneResourceName,
                cloneDescription,
                cloneDatasetName,
                cloneProjectName);

    assertResourceCommonFields(sourceDataset, cloneResourceName, cloneDescription, datasetToClone);
    assertControlledResourceCommonField(sourceDataset, datasetToClone);
    assertEquals(sourceDataset.getPrivateResourceState(), datasetToClone.getPrivateResourceState());
    assertEquals(cloneDatasetName, datasetToClone.getDatasetName());
    assertEquals(cloneProjectName, datasetToClone.getProjectId());
    List<ResourceLineageEntry> expectedLineage = new ArrayList<>();
    expectedLineage.add(
        new ResourceLineageEntry(sourceDataset.getWorkspaceId(), sourceDataset.getResourceId()));
    assertEquals(expectedLineage, datasetToClone.getResourceLineage());
  }

  @Test
  public void
      buildDestinationControlledBigQueryDataset_private_setPrivateResourceStateToInitializing() {
    var sourceDataset =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(WORKSPACE_ID)
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .privateResourceState(PrivateResourceState.ACTIVE)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                    .assignedUser("yuhuyoyo")
                    .build())
            .build();

    var datasetToClone =
        (ControlledBigQueryDatasetResource)
            WorkspaceCloneUtils.buildDestinationControlledBigQueryDataset(
                sourceDataset,
                DESTINATION_WORKSPACE_ID,
                DESTINATION_RESOURCE_ID,
                RandomStringUtils.randomAlphabetic(5),
                /*description=*/ null,
                RandomStringUtils.randomAlphabetic(5),
                "my-cloned-gcp-project");

    assertEquals(PrivateResourceState.INITIALIZING, datasetToClone.getPrivateResourceState().get());
    assertControlledResourceCommonField(sourceDataset, datasetToClone);
  }

  @Test
  public void buildDestinationControlledGcsBucket() {
    var sourceBucket =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(WORKSPACE_ID).build();
    var cloneResourceName = RandomStringUtils.randomAlphabetic(5);
    var cloneDescription = "This is a cloned bucket";
    var cloneBucketName = RandomStringUtils.randomAlphabetic(5).toLowerCase(Locale.ROOT);

    var bucketToClone =
        (ControlledGcsBucketResource)
            WorkspaceCloneUtils.buildDestinationControlledGcsBucket(
                sourceBucket,
                DESTINATION_WORKSPACE_ID,
                DESTINATION_RESOURCE_ID,
                cloneResourceName,
                cloneDescription,
                cloneBucketName);

    assertResourceCommonFields(sourceBucket, cloneResourceName, cloneDescription, bucketToClone);
    assertControlledResourceCommonField(sourceBucket, bucketToClone);
    assertEquals(sourceBucket.getPrivateResourceState(), bucketToClone.getPrivateResourceState());
    assertEquals(cloneBucketName, bucketToClone.getBucketName());
    List<ResourceLineageEntry> expectedLineage = new ArrayList<>();
    expectedLineage.add(
        new ResourceLineageEntry(sourceBucket.getWorkspaceId(), sourceBucket.getResourceId()));
    assertEquals(expectedLineage, bucketToClone.getResourceLineage());
  }

  private void assertResourceCommonFields(
      WsmResource sourceResource,
      String cloneResourceName,
      String cloneDescription,
      WsmResource resourceToClone) {
    assertEquals(cloneResourceName, resourceToClone.getName());
    assertEquals(cloneDescription, resourceToClone.getDescription());

    assertEquals(sourceResource.getCloningInstructions(), sourceResource.getCloningInstructions());
    assertEquals(1, resourceToClone.getResourceLineage().size());
    List<ResourceLineageEntry> expectedLineage = new ArrayList<>();
    expectedLineage.add(
        new ResourceLineageEntry(sourceResource.getWorkspaceId(), sourceResource.getResourceId()));
    assertEquals(expectedLineage, resourceToClone.getResourceLineage());
  }

  private void assertControlledResourceCommonField(
      ControlledResource sourceResource, ControlledResource resourceToClone) {
    assertEquals(sourceResource.getAccessScope(), resourceToClone.getAccessScope());
    assertEquals(sourceResource.getAssignedUser(), resourceToClone.getAssignedUser());
  }

  @Test
  public void createDestinationResourceLineage_sourceLineageIsNull() {
    var sourceWorkspaceUuid = UUID.randomUUID();
    var sourceResourceUuid = UUID.randomUUID();

    var destinationResourceLineage =
        WorkspaceCloneUtils.createDestinationResourceLineage(
            null, sourceWorkspaceUuid, sourceResourceUuid);

    List<ResourceLineageEntry> expectedLineage = new ArrayList<>();
    expectedLineage.add(new ResourceLineageEntry(sourceWorkspaceUuid, sourceResourceUuid));
    assertEquals(expectedLineage, destinationResourceLineage);
  }

  @Test
  public void createDestinationResourceLineage_sourceLineageIsNotEmpty() {
    var sourceWorkspaceUuid = UUID.randomUUID();
    var sourceResourceUuid = UUID.randomUUID();
    var sourceResourceLineageEntry = new ResourceLineageEntry(UUID.randomUUID(), UUID.randomUUID());
    var resourceLineage = new ArrayList<>(List.of(sourceResourceLineageEntry));

    var destinationResourceLineage =
        WorkspaceCloneUtils.createDestinationResourceLineage(
            resourceLineage, sourceWorkspaceUuid, sourceResourceUuid);

    resourceLineage.add(new ResourceLineageEntry(sourceWorkspaceUuid, sourceResourceUuid));
    assertEquals(resourceLineage, destinationResourceLineage);
  }
}
