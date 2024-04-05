package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.SPEND_PROFILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import bio.terra.stairway.*;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("unit")
public class CloneCreateCloudContextFlightTest extends BaseMockitoStrictStubbingTest {
  @Mock private FlightBeanBag flightBeanBag;
  @Mock private GcpCloudContextService gcpCloudContextService;
  @Mock private AzureCloudContextService azureCloudContextService;
  @Mock private SpendProfileService spendProfileService;
  @Mock private GcpCloudContext gcpCloudContext;
  private final AuthenticatedUserRequest userRequest = new AuthenticatedUserRequest();
  private final UUID sourceWorkspaceId = UUID.randomUUID();
  private final SpendProfileId sourceSpendProfileId =
      new SpendProfileId(UUID.randomUUID().toString());
  private final AzureCloudContext azureCloudContext =
      new AzureCloudContext(
          null,
          new CloudContextCommonFields(
              sourceSpendProfileId,
              WsmResourceState.READY,
              /* flightId= */ null,
              /* error= */ null));

  @BeforeEach
  public void beforeEach() {
    when(flightBeanBag.getGcpCloudContextService()).thenReturn(gcpCloudContextService);
    when(flightBeanBag.getAzureCloudContextService()).thenReturn(azureCloudContextService);
  }

  @Test
  public void testCreateFlightWithNoSourceCloudContext() {
    assertExpectedSteps(new ArrayList<>(List.of(CreateCloudContextIdsForFutureStepsStep.class)));
  }

  @Test
  public void testCreateFlightWithSourceGCPCloudContext() {
    when(gcpCloudContextService.getGcpCloudContext(eq(sourceWorkspaceId)))
        .thenReturn(Optional.of(gcpCloudContext));
    assertExpectedSteps(
        new ArrayList<>(
            List.of(
                CreateCloudContextIdsForFutureStepsStep.class,
                LaunchCreateCloudContextFlightStep.class,
                AwaitCreateCloudContextFlightStep.class)));
  }

  @Test
  public void testCreateFlightWithSourceAzureCloudContext() {
    when(azureCloudContextService.getAzureCloudContext(eq(sourceWorkspaceId)))
        .thenReturn(Optional.of(azureCloudContext));
    assertExpectedSteps(
        new ArrayList<>(
            List.of(
                CreateCloudContextIdsForFutureStepsStep.class,
                LaunchCreateCloudContextFlightStep.class,
                AwaitCreateCloudContextFlightStep.class)));
  }

  @Test
  public void testUsesSourceSpendProfileIfNoDestinationSpendProfile() {
    when(flightBeanBag.getSpendProfileService()).thenReturn(spendProfileService);
    FeatureConfiguration config = new FeatureConfiguration();
    // This is a misnomer... it is not specific to GCP (and in reality, always on).
    config.setBpmGcpEnabled(true);
    when(flightBeanBag.getFeatureConfiguration()).thenReturn(config);
    when(azureCloudContextService.getAzureCloudContext(eq(sourceWorkspaceId)))
        .thenReturn(Optional.of(azureCloudContext));

    FlightMap inputs = new FlightMap();
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    inputs.put(SOURCE_WORKSPACE_ID, sourceWorkspaceId);
    inputs.put(SPEND_PROFILE, null);
    new CloneCreateCloudContextFlight(inputs, flightBeanBag);

    verify(spendProfileService)
        .authorizeLinking(
            eq(sourceSpendProfileId),
            eq(true),
            argThat(a -> a.getReqId().equals(userRequest.getReqId())));
  }

  private void assertExpectedSteps(List<Class<? extends Step>> expectedSteps) {
    FlightMap inputs = new FlightMap();
    SpendProfile destinationSpendProfile =
        new SpendProfile(
            new SpendProfileId(UUID.randomUUID().toString()),
            CloudPlatform.AZURE,
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID().toString());
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    inputs.put(SOURCE_WORKSPACE_ID, sourceWorkspaceId);
    inputs.put(SPEND_PROFILE, destinationSpendProfile);
    var flight = new CloneCreateCloudContextFlight(inputs, flightBeanBag);

    assertEquals(expectedSteps.size(), flight.getSteps().size());
    assertEquals(
        0L,
        flight.getSteps().stream()
            .dropWhile((step -> expectedSteps.remove(0).equals(step.getClass())))
            .count());
  }
}
