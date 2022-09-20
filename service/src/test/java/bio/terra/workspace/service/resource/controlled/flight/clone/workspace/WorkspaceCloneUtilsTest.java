package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.ResourcePropertiesKey.FOLDER_ID_KEY;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeControlledResourceFieldsBuilder;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.uniqueDatasetId;
import static bio.terra.workspace.service.resource.controlled.flight.clone.workspace.WorkspaceCloneUtils.buildDestinationControlledBigQueryDataset;
import static bio.terra.workspace.service.resource.controlled.flight.clone.workspace.WorkspaceCloneUtils.buildDestinationControlledGcsBucket;
import static bio.terra.workspace.service.resource.controlled.flight.clone.workspace.WorkspaceCloneUtils.buildDestinationReferencedResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

public class WorkspaceCloneUtilsTest extends BaseUnitTest {
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID DESTINATION_WORKSPACE_ID = UUID.randomUUID();
  private static final UUID DESTINATION_RESOURCE_ID = UUID.randomUUID();

  @Test
  public void buildDestinationControlledBigQueryDataset_cloneSucceeds() {
    var sourceDataset =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(WORKSPACE_ID).build();
    var cloneResourceName = RandomStringUtils.randomAlphabetic(5);
    var cloneDescription = "This is a cloned dataset";
    var cloneDatasetName = RandomStringUtils.randomAlphabetic(5);
    var cloneProjectName = "my-cloned-gcp-project";

    ControlledBigQueryDatasetResource datasetToClone =
        buildDestinationControlledBigQueryDataset(
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
  }

  @Test
  public void
      buildDestinationControlledBigQueryDataset_private_setPrivateResourceStateToInitializing() {
    ControlledBigQueryDatasetResource sourceDataset =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(WORKSPACE_ID)
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .privateResourceState(PrivateResourceState.ACTIVE)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                    .assignedUser("yuhuyoyo")
                    .build())
            .build();

    ControlledBigQueryDatasetResource datasetToClone =
        buildDestinationControlledBigQueryDataset(
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
  public void buildDestinationControlledBigQueryDataset_clearSomeProperty() {
    var sourceDataset =
        ControlledBigQueryDatasetResource.builder()
            .common(makeControlledResourceFieldsWithCustomProperties())
            .datasetName(uniqueDatasetId())
            .projectId("my-gcp-project")
            .build();

    ControlledBigQueryDatasetResource datasetToClone =
        buildDestinationControlledBigQueryDataset(
            sourceDataset,
            DESTINATION_WORKSPACE_ID,
            DESTINATION_RESOURCE_ID,
            /*name=*/ RandomStringUtils.randomAlphabetic(5),
            /*description=*/ "This is a cloned dataset",
            /*cloudInstanceName=*/ RandomStringUtils.randomAlphabetic(5),
            /*destinationProjectId=*/ "my-cloned-gcp-project");

    ImmutableMap<String, String> properties = datasetToClone.getProperties();
    assertFalse(properties.containsKey(FOLDER_ID_KEY));
    assertEquals("bar", properties.get("foo"));
  }

  @Test
  public void buildDestinationControlledGcsBucket_cloneSucceeds() {
    ControlledGcsBucketResource sourceBucket =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(WORKSPACE_ID).build();
    var cloneResourceName = RandomStringUtils.randomAlphabetic(5);
    var cloneDescription = "This is a cloned bucket";
    // Gcs bucket cloud instance id must be lower-case.
    var cloneBucketName = RandomStringUtils.randomAlphabetic(5).toLowerCase(Locale.ROOT);

    ControlledGcsBucketResource bucketToClone =
        buildDestinationControlledGcsBucket(
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
  }

  @Test
  public void buildDestinationControlledGcsBucket_private_setPrivateResourceStateToInitializing() {
    var sourceBucket =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(WORKSPACE_ID)
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .privateResourceState(PrivateResourceState.ACTIVE)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                    .assignedUser("yuhuyoyo")
                    .build())
            .build();

    ControlledGcsBucketResource bucketToClone =
        buildDestinationControlledGcsBucket(
            sourceBucket,
            DESTINATION_WORKSPACE_ID,
            DESTINATION_RESOURCE_ID,
            RandomStringUtils.randomAlphabetic(5),
            "This is a cloned private bucket",
            // Gcs bucket cloud instance id must be lower-case.
            RandomStringUtils.randomAlphabetic(5).toLowerCase(Locale.ROOT));

    assertEquals(PrivateResourceState.INITIALIZING, bucketToClone.getPrivateResourceState().get());
    assertControlledResourceCommonField(sourceBucket, bucketToClone);
  }

  @Test
  public void buildDestinationControlledGcsBucket_clearSomeProperties() {
    ControlledGcsBucketResource sourceBucket =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(WORKSPACE_ID)
            .common(makeControlledResourceFieldsWithCustomProperties())
            .build();
    ControlledGcsBucketResource bucketToClone =
        buildDestinationControlledGcsBucket(
            sourceBucket,
            DESTINATION_WORKSPACE_ID,
            DESTINATION_RESOURCE_ID,
            /*name=*/ RandomStringUtils.randomAlphabetic(5),
            /*description=*/ "This is a cloned bucket",
            // Gcs bucket cloud instance id must be lower-case.
            /*cloudInstanceName=*/ RandomStringUtils.randomAlphabetic(5).toLowerCase(Locale.ROOT));

    ImmutableMap<String, String> properties = bucketToClone.getProperties();
    assertFalse(properties.containsKey(FOLDER_ID_KEY));
    assertEquals("bar", properties.get("foo"));
  }

  private static ControlledResourceFields makeControlledResourceFieldsWithCustomProperties() {
    return makeControlledResourceFieldsBuilder(WORKSPACE_ID)
        .properties(Map.of(FOLDER_ID_KEY, UUID.randomUUID().toString(), "foo", "bar"))
        .build();
  }

  @Test
  public void buildDestinationReferencedResource_attributesCopied() {
    ReferencedDataRepoSnapshotResource referencedResource =
        ReferenceResourceFixtures.makeDataRepoSnapshotResource(WORKSPACE_ID);
    var cloneResourceName = RandomStringUtils.randomAlphabetic(5);
    var cloneDescription = "This is a cloned data repo snapshot referenced resource";

    var snapshotToClone =
        (ReferencedDataRepoSnapshotResource)
            buildDestinationReferencedResource(
                referencedResource,
                DESTINATION_WORKSPACE_ID,
                DESTINATION_RESOURCE_ID,
                cloneResourceName,
                cloneDescription);

    assertResourceCommonFields(
        referencedResource, cloneResourceName, cloneDescription, snapshotToClone);
    assertEquals(referencedResource.getSnapshotId(), snapshotToClone.getSnapshotId());
    assertEquals(referencedResource.getInstanceName(), snapshotToClone.getInstanceName());
  }

  @Test
  public void buildDestinationReferencedResource_clearSomeProperties() {
    ReferencedDataRepoSnapshotResource referencedResource =
        ReferenceResourceFixtures.makeDataRepoSnapshotResource(WORKSPACE_ID);
    referencedResource.toBuilder()
        .wsmResourceFields(
            referencedResource.getWsmResourceFields().toBuilder()
                .properties(Map.of(FOLDER_ID_KEY, UUID.randomUUID().toString(), "foo", "bar"))
                .build())
        .build();

    var snapshotToClone =
        (ReferencedDataRepoSnapshotResource)
            buildDestinationReferencedResource(
                referencedResource,
                DESTINATION_WORKSPACE_ID,
                DESTINATION_RESOURCE_ID,
                /*name=*/ RandomStringUtils.randomAlphabetic(5),
                /*description=*/ "This is a cloned data repo snapshot referenced resource");

    ImmutableMap<String, String> properties = snapshotToClone.getProperties();
    assertFalse(properties.containsKey(FOLDER_ID_KEY));
    assertEquals("bar", properties.get("foo"));
  }

  private static void assertResourceCommonFields(
      WsmResource sourceResource,
      String cloneResourceName,
      String cloneDescription,
      WsmResource resourceToClone) {
    assertEquals(cloneResourceName, resourceToClone.getName());
    assertEquals(cloneDescription, resourceToClone.getDescription());

    assertEquals(sourceResource.getCloningInstructions(), resourceToClone.getCloningInstructions());
    assertEquals(sourceResource.getProperties(), resourceToClone.getProperties());
    assertEquals(1, resourceToClone.getResourceLineage().size());
    List<ResourceLineageEntry> expectedLineage = sourceResource.getResourceLineage();
    expectedLineage.add(
        new ResourceLineageEntry(sourceResource.getWorkspaceId(), sourceResource.getResourceId()));
    assertEquals(expectedLineage, resourceToClone.getResourceLineage());
  }

  private static void assertControlledResourceCommonField(
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
