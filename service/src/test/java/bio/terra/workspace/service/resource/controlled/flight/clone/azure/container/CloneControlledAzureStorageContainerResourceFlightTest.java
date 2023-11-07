package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.ClonedAzureResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("azureConnected")
public class CloneControlledAzureStorageContainerResourceFlightTest extends BaseAzureConnectedTest {

  @Autowired private JobService jobService;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private WorkspaceService workspaceService;

  private final AuthenticatedUserRequest userRequest =
      new AuthenticatedUserRequest().token(Optional.of("token"));

  @Test
  void cloneControlledAzureStorageContainer_overridesExistingResourceCloningInstructions()
      throws InterruptedException {

    var resource =
        ControlledAzureStorageContainerResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .build())
            .storageContainerName(UUID.randomUUID().toString())
            .build();

    FlightMap inputs = new FlightMap();
    inputs.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, resource);
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    inputs.put(WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS, "COPY_NOTHING");
    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.randomUUID());
    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.randomUUID());

    var result =
        StairwayTestUtils.blockUntilFlightCompletes(
            jobService.getStairway(),
            CloneControlledAzureStorageContainerResourceFlight.class,
            inputs,
            Duration.ofMinutes(1),
            null);

    var resultContainer =
        result
            .getResultMap()
            .get()
            .get(JobMapKeys.RESPONSE.getKeyName(), ClonedAzureResource.class);

    assertEquals(resultContainer.effectiveCloningInstructions(), CloningInstructions.COPY_NOTHING);
  }
}
