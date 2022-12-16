package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstant.DEFAULT_ZONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceApiFields;
import java.time.OffsetDateTime;
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
    ControlledResourceFields commonFields =
        ControlledResourceFixtures.makeNotebookCommonFieldsBuilder()
            .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
            .build();

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
            .instanceId("my-instance-id")
            .location("us-east1-b")
            .projectId("my-project-id")
            .build();

    ApiGcpAiNotebookInstanceResource apiResource =
        resource.toApiResource(
            new WsmResourceApiFields(MockMvcUtils.DEFAULT_USER_EMAIL, OffsetDateTime.now()));
    assertEquals("my-project-id", apiResource.getAttributes().getProjectId());
    assertEquals("us-east1-b", apiResource.getAttributes().getLocation());
    assertEquals("my-instance-id", apiResource.getAttributes().getInstanceId());
  }
}
