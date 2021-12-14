package bio.terra.workspace.service.resource.controlled;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceResource;
import org.junit.jupiter.api.Test;

public class ControlledAiNotebookInstanceResourceTest extends BaseUnitTest {
  @Test
  public void validateOk() {
    // will throw if anything is amiss.
    ControlledResourceFixtures.makeDefaultAiNotebookInstance().build().validate();
  }

  @Test
  public void validateNoRequiredFieldThrows() {
    assertThrows(
        MissingRequiredFieldException.class,
        () -> ControlledResourceFixtures.makeDefaultAiNotebookInstance().location(null).build());
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
  public void
      instanceIdIsNullAndUseResourceNameInstead_resourceNameIsInvalidInstanceId_generateAUniqueInstanceId() {
    String resourceName = "Ai_notebook_1";
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .name(resourceName)
            .instanceId(resourceName)
            .build();

    assertNotNull(resource.getInstanceId());
    assertNotEquals(resourceName, resource.getInstanceId());
  }

  @Test
  public void
      instanceIdIsNullAndUseResourceNameInstead_resourceNameIsValidInstanceId_usesResourceName() {
    String resourceName = "ai-notebook-1";
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .name(resourceName)
            .instanceId(resourceName)
            .build();

    assertEquals(resourceName, resource.getInstanceId());
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
