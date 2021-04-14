package bio.terra.workspace.service.resource.controlled;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
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
        () -> ControlledResourceFixtures.makeDefaultAiNotebookInstance().instanceId(null).build());
    assertThrows(
        MissingRequiredFieldException.class,
        () -> ControlledResourceFixtures.makeDefaultAiNotebookInstance().location(null).build());
  }

  @Test
  public void validateInstanceIdPattern() {
    assertThrows(
        InvalidReferenceException.class,
        () ->
            ControlledResourceFixtures.makeDefaultAiNotebookInstance()
                .instanceId("Invalid Instance Id %$^%$^")
                .build());
  }

  @Test
  public void testFlightMapSerialization() {
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance().build();

    FlightMap serializeMap = new FlightMap();
    serializeMap.put("resource", resource);

    FlightMap deserializedMap = new FlightMap();
    deserializedMap.fromJson(serializeMap.toJson());

    assertEquals(
        resource, deserializedMap.get("resource", ControlledAiNotebookInstanceResource.class));
  }
}
