package bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.generated.model.ApiGcpDataprocClusterResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import org.junit.jupiter.api.Test;

public class ControlledDataprocClusterResourceTest extends BaseUnitTest {
  @Test
  public void validateOk() {
    // will throw if anything is amiss.
    ControlledGcpResourceFixtures.makeDefaultDataprocCluster().build().validate();
  }

  @Test
  public void validateSharedAccessThrows() {
    ControlledResourceFields commonFields =
        ControlledGcpResourceFixtures.makeDataprocClusterCommonFieldsBuilder()
            .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
            .build();

    assertThrows(
        BadRequestException.class,
        () ->
            ControlledDataprocClusterResource.builder()
                .common(commonFields)
                .clusterId("a-cluster")
                .region("us-east1")
                .projectId("a-project-id")
                .build());
  }

  @Test
  public void testFlightMapSerialization() {
    ControlledDataprocClusterResource resource =
        ControlledGcpResourceFixtures.makeDefaultDataprocCluster().build();

    // TODO: [PF-935] Create a public API on FlightMap or Stairway test fixture that explicitly
    // tests that a type serializes and deserializes to the correct result.  For now leverage the
    // fact that we know that FlightMap internally serializes/deserializes on put/get.

    FlightMap flightMap = new FlightMap();
    flightMap.put("resource", resource);
    assertTrue(
        resource.partialEqual(flightMap.get("resource", ControlledDataprocClusterResource.class)));
  }

  @Test
  public void toApiResource() {
    ControlledDataprocClusterResource resource =
        ControlledGcpResourceFixtures.makeDefaultDataprocCluster()
            .clusterId("my-cluster-id")
            .region("us-east1")
            .projectId("my-project-id")
            .build();

    ApiGcpDataprocClusterResource apiResource = resource.toApiResource();
    assertEquals("my-project-id", apiResource.getAttributes().getProjectId());
    assertEquals("us-east1", apiResource.getAttributes().getRegion());
    assertEquals("my-cluster-id", apiResource.getAttributes().getClusterId());
  }
}
