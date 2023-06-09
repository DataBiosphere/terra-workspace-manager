package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.common.testfixtures.ControlledGcpResourceFixtures.makeDefaultAiNotebookInstanceBuilder;
import static bio.terra.workspace.common.testfixtures.ControlledGcpResourceFixtures.makeNotebookCommonFieldsBuilder;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstants.DEFAULT_ZONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import org.junit.jupiter.api.Test;

public class ControlledAiNotebookInstanceResourceTest extends BaseUnitTest {
  @Test
  public void validateOk() {
    // will throw if anything is amiss.
    makeDefaultAiNotebookInstanceBuilder().build().validate();
  }

  @Test
  public void resourceWithNullLocation_validatesOkAndSetsDefaultLocation() {
    ControlledAiNotebookInstanceResource resource =
        makeDefaultAiNotebookInstanceBuilder().location(null).build();

    resource.validate();
    assertEquals(DEFAULT_ZONE, resource.getLocation());
  }

  @Test
  public void validateSharedAccessThrows() {
    ControlledResourceFields commonFields =
        makeNotebookCommonFieldsBuilder().accessScope(AccessScopeType.ACCESS_SCOPE_SHARED).build();

    assertThrows(
        BadRequestException.class,
        () ->
            ControlledAiNotebookInstanceResource.builder()
                .common(commonFields)
                .instanceId("an-instance")
                .location("us-east1-b")
                .projectId("a-projecct-id")
                .build());
  }

  @Test
  public void testFlightMapSerialization() {
    ControlledAiNotebookInstanceResource resource = makeDefaultAiNotebookInstanceBuilder().build();

    // TODO: [PF-935] Create a public API on FlightMap or Stairway test fixture that explicitly
    // tests that a type serializes and deserializes to the correct result.  For now leverage the
    // fact that we know that FlightMap internally serializes/deserializes on put/get.

    FlightMap flightMap = new FlightMap();
    flightMap.put("resource", resource);
    assertTrue(
        resource.partialEqual(
            flightMap.get("resource", ControlledAiNotebookInstanceResource.class)));
  }

  @Test
  public void toApiResource() {
    ControlledAiNotebookInstanceResource resource =
        makeDefaultAiNotebookInstanceBuilder()
            .instanceId("my-instance-id")
            .location("us-east1-b")
            .projectId("my-project-id")
            .build();

    ApiGcpAiNotebookInstanceResource apiResource = resource.toApiResource();
    assertEquals("my-project-id", apiResource.getAttributes().getProjectId());
    assertEquals("us-east1-b", apiResource.getAttributes().getLocation());
    assertEquals("my-instance-id", apiResource.getAttributes().getInstanceId());
  }
}
