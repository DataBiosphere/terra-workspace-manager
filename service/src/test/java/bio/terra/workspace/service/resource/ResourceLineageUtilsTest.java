package bio.terra.workspace.service.resource;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotAttributes;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ResourceLineageUtilsTest extends BaseSpringBootUnitTest {

  @Test
  public void constructResourceNullLineage_resourceLineageEmptyArray() {
    UUID randomId = UUID.randomUUID();
    var resource =
        ReferencedDataRepoSnapshotResource.builder()
            .wsmResourceFields(
                ReferenceResourceFixtures.makeDefaultWsmResourceFieldBuilder(randomId)
                    .resourceLineage(null)
                    .build())
            .instanceName("terra")
            .snapshotId("polaroid")
            .build();

    assertEquals(new ArrayList<>(), resource.getResourceLineage());
  }

  @Test
  public void constructResourceFromDbNullLineage_resourceLineageEmptyArray() {
    UUID randomId = UUID.randomUUID();
    String resourceName = "testdatarepo-" + randomId;
    Map<String, String> propertyMap = new HashMap<>();

    var attributes = new ReferencedDataRepoSnapshotAttributes("terra", "polaroid");
    String attributesJson = DbSerDes.toJson(attributes);

    var dbResource =
        new DbResource()
            .workspaceUuid(randomId)
            .cloudPlatform(CloudPlatform.ANY)
            .resourceId(randomId)
            .name(resourceName)
            .description("description of " + resourceName)
            .stewardshipType(StewardshipType.REFERENCED)
            .resourceType(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT)
            .cloningInstructions(CloningInstructions.COPY_REFERENCE)
            .attributes(attributesJson)
            .resourceLineage(null)
            .properties(propertyMap)
            .createdByEmail(DEFAULT_USER_EMAIL)
            .createdDate(OffsetDateTime.now());

    var resource = new ReferencedDataRepoSnapshotResource(dbResource);
    assertEquals(new ArrayList<>(), resource.getResourceLineage());
  }

  @Test
  public void constructResourceWithLineage_matchingLineageArray() {
    UUID randomId = UUID.randomUUID();
    var lineageEntry = new ResourceLineageEntry(randomId, randomId);
    var lineage = new ArrayList<ResourceLineageEntry>();
    lineage.add(lineageEntry);

    var resource =
        ReferencedDataRepoSnapshotResource.builder()
            .wsmResourceFields(
                ReferenceResourceFixtures.makeDefaultWsmResourceFieldBuilder(randomId)
                    .resourceLineage(lineage)
                    .build())
            .instanceName("terra")
            .snapshotId("polaroid")
            .build();

    assertEquals(lineage, resource.getResourceLineage());
  }

  @Test
  public void constructResourceFromDbWithLineage_matchingLineageEmptyArray() {
    UUID randomId = UUID.randomUUID();
    String resourceName = "testdatarepo-" + randomId;
    Map<String, String> propertyMap = new HashMap<>();
    var lineageEntry = new ResourceLineageEntry(randomId, randomId);
    var lineage = new ArrayList<ResourceLineageEntry>();
    lineage.add(lineageEntry);

    var attributes = new ReferencedDataRepoSnapshotAttributes("terra", "polaroid");
    String attributesJson = DbSerDes.toJson(attributes);

    var dbResource =
        new DbResource()
            .workspaceUuid(randomId)
            .cloudPlatform(CloudPlatform.ANY)
            .resourceId(randomId)
            .name(resourceName)
            .description("description of " + resourceName)
            .stewardshipType(StewardshipType.REFERENCED)
            .resourceType(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT)
            .cloningInstructions(CloningInstructions.COPY_REFERENCE)
            .attributes(attributesJson)
            .resourceLineage(lineage)
            .properties(propertyMap)
            .createdByEmail(DEFAULT_USER_EMAIL)
            .createdDate(OffsetDateTime.now());

    var resource = new ReferencedDataRepoSnapshotResource(dbResource);
    assertEquals(lineage, resource.getResourceLineage());
  }
}
