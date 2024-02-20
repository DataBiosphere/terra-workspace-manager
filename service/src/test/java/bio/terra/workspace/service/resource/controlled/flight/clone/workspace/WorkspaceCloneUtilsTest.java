package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.utils.GcpUtilsTest.DEFAULT_GCP_RESOURCE_REGION;
import static bio.terra.workspace.service.resource.controlled.flight.clone.workspace.WorkspaceCloneUtils.buildDestinationControlledBigQueryDataset;
import static bio.terra.workspace.service.resource.controlled.flight.clone.workspace.WorkspaceCloneUtils.buildDestinationControlledGcsBucket;
import static bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties.FOLDER_ID_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotResource;
import com.google.common.collect.ImmutableMap;
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
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(WORKSPACE_ID).build();
    var cloneResourceName = RandomStringUtils.randomAlphabetic(5);
    var cloneDescription = "This is a cloned dataset";
    var cloneDatasetName = RandomStringUtils.randomAlphabetic(5);
    var cloneProjectName = "my-cloned-gcp-project";

    ControlledBigQueryDatasetResource datasetToClone =
        buildDestinationControlledBigQueryDataset(
            sourceDataset,
            DESTINATION_WORKSPACE_ID,
            DESTINATION_RESOURCE_ID,
            /* destinationFolderId= */ null,
            cloneResourceName,
            cloneDescription,
            cloneDatasetName,
            cloneProjectName,
            DEFAULT_USER_EMAIL,
            DEFAULT_GCP_RESOURCE_REGION,
            /* defaultTableLifetime= */ null,
            /* defaultPartitionLifetime= */ null);

    assertResourceCommonFields(sourceDataset, cloneResourceName, cloneDescription, datasetToClone);
    assertControlledResourceCommonField(sourceDataset, datasetToClone);
    assertEquals(sourceDataset.getPrivateResourceState(), datasetToClone.getPrivateResourceState());
    assertEquals(cloneDatasetName, datasetToClone.getDatasetName());
    assertEquals(cloneProjectName, datasetToClone.getProjectId());
  }

  @Test
  public void
      buildDestinationControlledBigQueryDataset_nameAndDescriptionIsNull_preserveSourceResourceNameAndDescription() {
    var sourceDataset =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(WORKSPACE_ID).build();
    var cloneDatasetName = RandomStringUtils.randomAlphabetic(5);
    var cloneProjectName = "my-cloned-gcp-project";

    ControlledBigQueryDatasetResource datasetToClone =
        buildDestinationControlledBigQueryDataset(
            sourceDataset,
            DESTINATION_WORKSPACE_ID,
            DESTINATION_RESOURCE_ID,
            /* destinationFolderId= */ null,
            /* name= */ null,
            /* description= */ null,
            cloneDatasetName,
            cloneProjectName,
            DEFAULT_USER_EMAIL,
            DEFAULT_GCP_RESOURCE_REGION,
            /* defaultTableLifetime= */ null,
            /* defaultPartitionLifetime= */ null);

    assertResourceCommonFields(
        sourceDataset, sourceDataset.getName(), sourceDataset.getDescription(), datasetToClone);
    assertControlledResourceCommonField(sourceDataset, datasetToClone);
    assertEquals(sourceDataset.getPrivateResourceState(), datasetToClone.getPrivateResourceState());
    assertEquals(cloneDatasetName, datasetToClone.getDatasetName());
    assertEquals(cloneProjectName, datasetToClone.getProjectId());
  }

  @Test
  public void
      buildDestinationControlledBigQueryDataset_private_setPrivateResourceStateToInitializing() {
    ControlledBigQueryDatasetResource sourceDataset =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(WORKSPACE_ID)
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
            /* destinationFolderId= */ null,
            RandomStringUtils.randomAlphabetic(5),
            /* description= */ null,
            RandomStringUtils.randomAlphabetic(5),
            "my-cloned-gcp-project",
            DEFAULT_USER_EMAIL,
            DEFAULT_GCP_RESOURCE_REGION,
            /* defaultTableLifetime= */ null,
            /* defaultPartitionLifetime= */ null);

    assertEquals(PrivateResourceState.INITIALIZING, datasetToClone.getPrivateResourceState().get());
    assertControlledResourceCommonField(sourceDataset, datasetToClone);
  }

  @Test
  public void buildDestinationControlledBigQueryDataset_clearSomeProperty() {
    var sourceDataset =
        ControlledBigQueryDatasetResource.builder()
            .common(makeControlledResourceFieldsWithCustomProperties())
            .datasetName(ControlledGcpResourceFixtures.uniqueDatasetId())
            .projectId("my-gcp-project")
            .build();

    ControlledBigQueryDatasetResource datasetToClone =
        buildDestinationControlledBigQueryDataset(
            sourceDataset,
            DESTINATION_WORKSPACE_ID,
            DESTINATION_RESOURCE_ID,
            /* destinationFolderId= */ null,
            /* name= */ RandomStringUtils.randomAlphabetic(5),
            /* description= */ "This is a cloned dataset",
            /* cloudInstanceName= */ RandomStringUtils.randomAlphabetic(5),
            /* destinationProjectId= */ "my-cloned-gcp-project",
            DEFAULT_USER_EMAIL,
            DEFAULT_GCP_RESOURCE_REGION,
            /* defaultTableLifetime= */ null,
            /* defaultPartitionLifetime= */ null);

    ImmutableMap<String, String> properties = datasetToClone.getProperties();
    assertFalse(properties.containsKey(FOLDER_ID_KEY));
    assertEquals("bar", properties.get("foo"));
  }

  @Test
  public void buildDestinationControlledBigQueryDataset_cloneToTheSameWorkspace_notClearProperty() {
    var sourceDataset =
        ControlledBigQueryDatasetResource.builder()
            .common(makeControlledResourceFieldsWithCustomProperties())
            .datasetName(ControlledGcpResourceFixtures.uniqueDatasetId())
            .projectId("my-gcp-project")
            .build();

    ControlledBigQueryDatasetResource datasetToClone =
        buildDestinationControlledBigQueryDataset(
            sourceDataset,
            sourceDataset.getWorkspaceId(),
            DESTINATION_RESOURCE_ID,
            /* destinationFolderId= */ null,
            /* name= */ RandomStringUtils.randomAlphabetic(5),
            /* description= */ "This is a cloned dataset",
            /* cloudInstanceName= */ RandomStringUtils.randomAlphabetic(5),
            /* destinationProjectId= */ "my-gcp-project",
            DEFAULT_USER_EMAIL,
            DEFAULT_GCP_RESOURCE_REGION,
            /* defaultTableLifetime= */ null,
            /* defaultPartitionLifetime= */ null);

    assertTrue(datasetToClone.getProperties().containsKey(FOLDER_ID_KEY));
  }

  @Test
  public void buildDestinationControlledGcsBucket_cloneSucceeds() {
    ControlledGcsBucketResource sourceBucket =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(WORKSPACE_ID).build();
    var cloneResourceName = RandomStringUtils.randomAlphabetic(5);
    var cloneDescription = "This is a cloned bucket";
    // Gcs bucket cloud instance id must be lower-case.
    var cloneBucketName = RandomStringUtils.randomAlphabetic(5).toLowerCase(Locale.ROOT);

    ControlledGcsBucketResource bucketToClone =
        buildDestinationControlledGcsBucket(
            sourceBucket,
            DESTINATION_WORKSPACE_ID,
            DESTINATION_RESOURCE_ID,
            /* destinationFolderId= */ null,
            cloneResourceName,
            cloneDescription,
            cloneBucketName,
            DEFAULT_USER_EMAIL,
            DEFAULT_GCP_RESOURCE_REGION);

    assertResourceCommonFields(sourceBucket, cloneResourceName, cloneDescription, bucketToClone);
    assertControlledResourceCommonField(sourceBucket, bucketToClone);
    assertEquals(sourceBucket.getPrivateResourceState(), bucketToClone.getPrivateResourceState());
    assertEquals(cloneBucketName, bucketToClone.getBucketName());
  }

  @Test
  public void
      buildDestinationControlledGcsBucket_nameAndDescriptionIsNull_preserveSourceResourceNameAndDescription() {
    ControlledGcsBucketResource sourceBucket =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(WORKSPACE_ID).build();
    // Gcs bucket cloud instance id must be lower-case.
    var cloneBucketName = RandomStringUtils.randomAlphabetic(5).toLowerCase(Locale.ROOT);

    ControlledGcsBucketResource bucketToClone =
        buildDestinationControlledGcsBucket(
            sourceBucket,
            DESTINATION_WORKSPACE_ID,
            DESTINATION_RESOURCE_ID,
            /* destinationFolderId= */ null,
            /* name= */ null,
            /* description= */ null,
            cloneBucketName,
            DEFAULT_USER_EMAIL,
            DEFAULT_GCP_RESOURCE_REGION);

    assertResourceCommonFields(
        sourceBucket, sourceBucket.getName(), sourceBucket.getDescription(), bucketToClone);
    assertControlledResourceCommonField(sourceBucket, bucketToClone);
    assertEquals(sourceBucket.getPrivateResourceState(), bucketToClone.getPrivateResourceState());
    assertEquals(cloneBucketName, bucketToClone.getBucketName());
  }

  @Test
  public void buildDestinationControlledGcsBucket_private_setPrivateResourceStateToInitializing() {
    var sourceBucket =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(WORKSPACE_ID)
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
            /* destinationFolderId= */ null,
            RandomStringUtils.randomAlphabetic(5),
            "This is a cloned private bucket",
            // Gcs bucket cloud instance id must be lower-case.
            TestUtils.appendRandomNumber("gcsbucket"),
            DEFAULT_USER_EMAIL,
            DEFAULT_GCP_RESOURCE_REGION);

    assertEquals(PrivateResourceState.INITIALIZING, bucketToClone.getPrivateResourceState().get());
    assertControlledResourceCommonField(sourceBucket, bucketToClone);
  }

  @Test
  public void buildDestinationControlledGcsBucket_clearSomeProperties() {
    ControlledGcsBucketResource sourceBucket =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(WORKSPACE_ID)
            .common(makeControlledResourceFieldsWithCustomProperties())
            .build();
    ControlledGcsBucketResource bucketToClone =
        buildDestinationControlledGcsBucket(
            sourceBucket,
            DESTINATION_WORKSPACE_ID,
            DESTINATION_RESOURCE_ID,
            /* destinationFolderId= */ null,
            /* name= */ RandomStringUtils.randomAlphabetic(5),
            /* description= */ "This is a cloned bucket",
            // Gcs bucket cloud instance id must be lower-case.
            /* cloudInstanceName= */ TestUtils.appendRandomNumber("gcsbucket"),
            DEFAULT_USER_EMAIL,
            DEFAULT_GCP_RESOURCE_REGION);

    ImmutableMap<String, String> properties = bucketToClone.getProperties();
    assertFalse(properties.containsKey(FOLDER_ID_KEY));
    assertEquals("bar", properties.get("foo"));
  }

  private static ControlledResourceFields makeControlledResourceFieldsWithCustomProperties() {
    return ControlledResourceFixtures.makeControlledResourceFieldsBuilder(WORKSPACE_ID)
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
            referencedResource.buildReferencedClone(
                DESTINATION_WORKSPACE_ID,
                DESTINATION_RESOURCE_ID,
                /* destinationFolderId= */ null,
                cloneResourceName,
                cloneDescription,
                DEFAULT_USER_EMAIL);

    assertResourceCommonFields(
        referencedResource, cloneResourceName, cloneDescription, snapshotToClone);
    assertEquals(referencedResource.getSnapshotId(), snapshotToClone.getSnapshotId());
    assertEquals(referencedResource.getInstanceName(), snapshotToClone.getInstanceName());
  }

  @Test
  public void buildDestinationReferencedResource_clearSomeProperties() {
    ReferencedDataRepoSnapshotResource referencedResource =
        ReferenceResourceFixtures.makeDataRepoSnapshotResource(WORKSPACE_ID);
    referencedResource =
        referencedResource.toBuilder()
            .wsmResourceFields(
                referencedResource.getWsmResourceFields().toBuilder()
                    .properties(Map.of(FOLDER_ID_KEY, UUID.randomUUID().toString(), "foo", "bar"))
                    .build())
            .build();

    var snapshotToClone =
        (ReferencedDataRepoSnapshotResource)
            referencedResource.buildReferencedClone(
                DESTINATION_WORKSPACE_ID,
                DESTINATION_RESOURCE_ID,
                /* destinationFolderId= */ null,
                /* name= */ RandomStringUtils.randomAlphabetic(5),
                /* description= */ "This is a cloned data repo snapshot referenced resource",
                DEFAULT_USER_EMAIL);

    ImmutableMap<String, String> properties = snapshotToClone.getProperties();
    assertFalse(properties.containsKey(FOLDER_ID_KEY));
    assertEquals("bar", properties.get("foo"));
  }

  @Test
  public void buildDestinationReferencedResource_cloneToSameWorkspace_notClearProperties() {
    ReferencedDataRepoSnapshotResource referencedResource =
        ReferenceResourceFixtures.makeDataRepoSnapshotResource(WORKSPACE_ID);
    referencedResource =
        referencedResource.toBuilder()
            .wsmResourceFields(
                referencedResource.getWsmResourceFields().toBuilder()
                    .properties(Map.of(FOLDER_ID_KEY, UUID.randomUUID().toString(), "foo", "bar"))
                    .build())
            .build();

    var snapshotToClone =
        (ReferencedDataRepoSnapshotResource)
            referencedResource.buildReferencedClone(
                referencedResource.getWorkspaceId(),
                DESTINATION_RESOURCE_ID,
                /* destinationFolderId= */ null,
                /* name= */ RandomStringUtils.randomAlphabetic(5),
                /* description= */ "This is a cloned data repo snapshot referenced resource",
                DEFAULT_USER_EMAIL);

    assertTrue(snapshotToClone.getProperties().containsKey(FOLDER_ID_KEY));
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
    assertEquals(DEFAULT_GCP_RESOURCE_REGION, resourceToClone.getRegion());
  }
}
