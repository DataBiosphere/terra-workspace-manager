package bio.terra.workspace.service.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotAttributes;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ResourceLineageUtilsTest extends BaseUnitTest {

  @Test
  public void constructResourceNullLineage_resourceLineageEmptyArray() {
    UUID randomId = UUID.randomUUID();
    String resourceName = "testdatarepo-" + randomId;
    Map<String, String> propertyMap = new HashMap<>();

    var resource =
        new ReferencedDataRepoSnapshotResource(
            randomId,
            randomId,
            resourceName,
            "description of " + resourceName,
            CloningInstructions.COPY_NOTHING,
            "terra",
            "polaroid",
            /*resourceLineage=*/ null,
            propertyMap,
            "foo@gmail.com",
            /*createdDate*/null);

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
            .cloudResourceType(WsmResourceFamily.DATA_REPO_SNAPSHOT)
            .resourceType(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT)
            .cloningInstructions(CloningInstructions.COPY_REFERENCE)
            .attributes(attributesJson)
            .resourceLineage(null)
            .properties(propertyMap);

    var resource = new ReferencedDataRepoSnapshotResource(dbResource);
    assertEquals(new ArrayList<>(), resource.getResourceLineage());
  }

  @Test
  public void constructResourceWithLineage_matchingLineageArray() {
    UUID randomId = UUID.randomUUID();
    String resourceName = "testdatarepo-" + randomId;
    Map<String, String> propertyMap = new HashMap<>();
    var lineageEntry = new ResourceLineageEntry(randomId, randomId);
    var lineage = new ArrayList<ResourceLineageEntry>();
    lineage.add(lineageEntry);

    var resource =
        new ReferencedDataRepoSnapshotResource(
            randomId,
            randomId,
            resourceName,
            "description of " + resourceName,
            CloningInstructions.COPY_NOTHING,
            "terra",
            "polaroid",
            lineage,
            propertyMap,
            "foo@gmail.com",
            /*createdDate*/null);

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
            .cloudResourceType(WsmResourceFamily.DATA_REPO_SNAPSHOT)
            .resourceType(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT)
            .cloningInstructions(CloningInstructions.COPY_REFERENCE)
            .attributes(attributesJson)
            .resourceLineage(lineage)
            .properties(propertyMap);

    var resource = new ReferencedDataRepoSnapshotResource(dbResource);
    assertEquals(lineage, resource.getResourceLineage());
  }
}
