package bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstants.DEFAULT_ZONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.generated.model.ApiGcpGceInstanceResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import org.junit.jupiter.api.Test;

public class ControlledGceInstanceResourceTest extends BaseSpringBootUnitTest {
  @Test
  public void validateOk() {
    // will throw if anything is amiss.
    ControlledGcpResourceFixtures.makeDefaultGceInstance().build().validate();
  }

  @Test
  public void resourceWithNullLocation_validatesOkAndSetsDefaultLocation() {
    ControlledGceInstanceResource resource =
        ControlledGcpResourceFixtures.makeDefaultGceInstance().zone(null).build();

    resource.validate();
    assertEquals(DEFAULT_ZONE, resource.getZone());
  }

  @Test
  public void validateSharedAccessThrows() {
    ControlledResourceFields commonFields =
        ControlledGcpResourceFixtures.makeGceInstanceCommonFieldsBuilder()
            .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
            .build();

    assertThrows(
        BadRequestException.class,
        () ->
            ControlledGceInstanceResource.builder()
                .common(commonFields)
                .instanceId("an-instance")
                .zone("us-east1-b")
                .projectId("a-projecct-id")
                .build());
  }

  @Test
  public void testFlightMapSerialization() {
    ControlledGceInstanceResource resource =
        ControlledGcpResourceFixtures.makeDefaultGceInstance().build();

    // TODO: [PF-935] Create a public API on FlightMap or Stairway test fixture that explicitly
    // tests that a type serializes and deserializes to the correct result.  For now leverage the
    // fact that we know that FlightMap internally serializes/deserializes on put/get.

    FlightMap flightMap = new FlightMap();
    flightMap.put("resource", resource);
    assertTrue(
        resource.partialEqual(flightMap.get("resource", ControlledGceInstanceResource.class)));
  }

  @Test
  public void toApiResource() {
    ControlledGceInstanceResource resource =
        ControlledGcpResourceFixtures.makeDefaultGceInstance()
            .instanceId("my-instance-id")
            .zone("us-east1-b")
            .projectId("my-project-id")
            .build();

    ApiGcpGceInstanceResource apiResource = resource.toApiResource();
    assertEquals("my-project-id", apiResource.getAttributes().getProjectId());
    assertEquals("us-east1-b", apiResource.getAttributes().getZone());
    assertEquals("my-instance-id", apiResource.getAttributes().getInstanceId());
  }
}
