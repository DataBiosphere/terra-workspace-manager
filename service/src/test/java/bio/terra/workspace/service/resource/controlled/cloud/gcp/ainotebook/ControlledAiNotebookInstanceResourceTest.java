package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstant.DEFAULT_ZONE;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource.AUTO_NAME_DATE_FORMAT;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource.MAX_INSTANCE_NAME_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import org.junit.jupiter.api.Test;

public class ControlledAiNotebookInstanceResourceTest extends BaseUnitTest {
  @Test
  public void validateOk() {
    // will throw if anything is amiss.
    ControlledResourceFixtures.makeDefaultAiNotebookInstance().build().validate();
  }

  @Test
  public void resourceWithNullLocation_validatesOkAndSetsDefaultLocation() {
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance().location(null).build();

    resource.validate();
    assertEquals(DEFAULT_ZONE, resource.getLocation());
  }

  @Test
  public void validateSharedAccessThrows() {
    assertThrows(
        BadRequestException.class,
        () ->
            ControlledResourceFixtures.makeDefaultAiNotebookInstance()
                .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                .build());
  }

  @Test
  public void testFlightMapSerialization() {
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance().build();

    // TODO: [PF-935] Create a public API on FlightMap or Stairway test fixture that explicitly
    // tests that a type serializes and deserializes to the correct result.  For now leverage the
    // fact that we know that FlightMap internally serializes/deserializes on put/get.

    FlightMap flightMap = new FlightMap();
    flightMap.put("resource", resource);
    assertEquals(resource, flightMap.get("resource", ControlledAiNotebookInstanceResource.class));
  }

  @Test
  public void generateInstanceId() {
    String userEmail = "yuhuyoyo@google.com";
    String instanceId = ControlledAiNotebookInstanceResource.generateInstanceId(userEmail);

    assertTrue(instanceId.startsWith("yuhuyoyo-"));
  }

  @Test
  public void generateInstanceId_userIdHasDash_removeDashes() {
    String userEmail = "yu_hu_yo_yo@google.com";
    String instanceId = ControlledAiNotebookInstanceResource.generateInstanceId(userEmail);

    assertTrue(instanceId.startsWith("yuhuyoyo-"));
  }

  @Test
  public void generateInstanceId_userIdIsNull_prefixWithNotebook() {
    String instanceId = ControlledAiNotebookInstanceResource.generateInstanceId(null);

    assertTrue(instanceId.startsWith("notebook"));
  }

  @Test
  public void generateInstanceId_userIdIsEmpty_prefixWithNotebook() {
    String instanceId = ControlledAiNotebookInstanceResource.generateInstanceId("");

    assertTrue(instanceId.startsWith("notebook"));
  }

  @Test
  public void generateInstanceId_userIdHasUppercase_toLowerCase() {
    String userEmail = "YUHUYOYO@google.com";
    String instanceId = ControlledAiNotebookInstanceResource.generateInstanceId(userEmail);

    assertTrue(instanceId.startsWith("yuhuyoyo-"));
  }

  @Test
  public void generateInstanceId_userIdTooLong_trim() {
    String userEmail =
        "yuhuyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyoyo@google.com";
    String instanceId = ControlledAiNotebookInstanceResource.generateInstanceId(userEmail);

    int maxNameLength = MAX_INSTANCE_NAME_LENGTH - AUTO_NAME_DATE_FORMAT.length();

    assertTrue(instanceId.startsWith(instanceId.substring(0, maxNameLength)));
  }

  @Test
  public void toApiResource() {
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .name("my-notebook")
            .instanceId("my-instance-id")
            .location("us-east1-b")
            .build();

    ApiGcpAiNotebookInstanceResource apiResource = resource.toApiResource("my-project-id");
    assertEquals("my-notebook", apiResource.getMetadata().getName());
    assertEquals("my-project-id", apiResource.getAttributes().getProjectId());
    assertEquals("us-east1-b", apiResource.getAttributes().getLocation());
    assertEquals("my-instance-id", apiResource.getAttributes().getInstanceId());
  }
}
