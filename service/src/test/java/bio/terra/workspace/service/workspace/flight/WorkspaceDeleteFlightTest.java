package bio.terra.workspace.service.workspace.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.cloud.aws.DeleteAwsContextStep;
import bio.terra.workspace.service.workspace.flight.cloud.aws.DeleteControlledAwsResourcesStep;
import bio.terra.workspace.service.workspace.flight.cloud.azure.DeleteAzureContextStep;
import bio.terra.workspace.service.workspace.flight.cloud.azure.DeleteControlledAzureResourcesStep;
import bio.terra.workspace.service.workspace.flight.cloud.gcp.DeleteGcpProjectStep;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteControlledSamResourcesStep;
import bio.terra.workspace.service.workspace.flight.delete.workspace.DeleteWorkspaceAuthzStep;
import bio.terra.workspace.service.workspace.flight.delete.workspace.DeleteWorkspacePoliciesStep;
import bio.terra.workspace.service.workspace.flight.delete.workspace.DeleteWorkspaceStateStep;
import bio.terra.workspace.service.workspace.flight.delete.workspace.EnsureNoWorkspaceChildrenStep;
import bio.terra.workspace.service.workspace.flight.delete.workspace.WorkspaceDeleteFlight;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.ActiveProfiles;

// This doesn't extend BaseUnitTest because we don't need BaseUnitTestMocks
@Tag("unit")
@ActiveProfiles({"unit-test"})
public class WorkspaceDeleteFlightTest {

  @Test
  void stepsForRawlsWorkspace() {
    var flightMap = new FlightMap();
    flightMap.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), new AuthenticatedUserRequest());
    flightMap.put(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.randomUUID().toString());
    flightMap.put(WorkspaceFlightMapKeys.WORKSPACE_STAGE, WorkspaceStage.RAWLS_WORKSPACE.name());

    var featureConfig = new FeatureConfiguration();
    featureConfig.setTpsEnabled(true);
    var context = Mockito.mock(FlightBeanBag.class, Mockito.RETURNS_MOCKS);
    when(context.getFeatureConfiguration()).thenReturn(featureConfig);

    var flight = new WorkspaceDeleteFlight(flightMap, context);
    var steps = flight.getSteps();
    assertEquals(8, steps.size());
    assertEquals(DeleteControlledAzureResourcesStep.class, steps.get(0).getClass());
    assertEquals(DeleteControlledAwsResourcesStep.class, steps.get(1).getClass());
    assertEquals(DeleteControlledSamResourcesStep.class, steps.get(2).getClass());
    assertEquals(DeleteGcpProjectStep.class, steps.get(3).getClass());
    assertEquals(EnsureNoWorkspaceChildrenStep.class, steps.get(4).getClass());
    assertEquals(DeleteAzureContextStep.class, steps.get(5).getClass());
    assertEquals(DeleteAwsContextStep.class, steps.get(6).getClass());
    assertEquals(DeleteWorkspaceStateStep.class, steps.get(7).getClass());
  }

  @Test
  void stepsForMCWorkspaceWithTpsEnabled() {
    var flightMap = new FlightMap();
    flightMap.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), new AuthenticatedUserRequest());
    flightMap.put(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.randomUUID().toString());
    flightMap.put(WorkspaceFlightMapKeys.WORKSPACE_STAGE, WorkspaceStage.MC_WORKSPACE.name());

    var featureConfig = new FeatureConfiguration();
    featureConfig.setTpsEnabled(true);
    var context = Mockito.mock(FlightBeanBag.class, Mockito.RETURNS_MOCKS);
    when(context.getFeatureConfiguration()).thenReturn(featureConfig);

    var flight = new WorkspaceDeleteFlight(flightMap, context);
    var steps = flight.getSteps();
    assertEquals(10, steps.size());
    assertEquals(DeleteControlledAzureResourcesStep.class, steps.get(0).getClass());
    assertEquals(DeleteControlledAwsResourcesStep.class, steps.get(1).getClass());
    assertEquals(DeleteControlledSamResourcesStep.class, steps.get(2).getClass());
    assertEquals(DeleteGcpProjectStep.class, steps.get(3).getClass());
    assertEquals(EnsureNoWorkspaceChildrenStep.class, steps.get(4).getClass());
    assertEquals(DeleteAzureContextStep.class, steps.get(5).getClass());
    assertEquals(DeleteAwsContextStep.class, steps.get(6).getClass());
    assertEquals(DeleteWorkspacePoliciesStep.class, steps.get(7).getClass());
    assertEquals(DeleteWorkspaceAuthzStep.class, steps.get(8).getClass());
    assertEquals(DeleteWorkspaceStateStep.class, steps.get(9).getClass());
  }

  @Test
  void stepsForMCWorkspaceWithoutTpsEnabled() {
    var flightMap = new FlightMap();
    flightMap.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), new AuthenticatedUserRequest());
    flightMap.put(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.randomUUID().toString());
    flightMap.put(WorkspaceFlightMapKeys.WORKSPACE_STAGE, WorkspaceStage.MC_WORKSPACE.name());

    var featureConfig = new FeatureConfiguration();
    featureConfig.setTpsEnabled(false);
    var context = Mockito.mock(FlightBeanBag.class, Mockito.RETURNS_MOCKS);
    when(context.getFeatureConfiguration()).thenReturn(featureConfig);

    var flight = new WorkspaceDeleteFlight(flightMap, context);
    var steps = flight.getSteps();
    assertEquals(9, steps.size());
    assertEquals(DeleteControlledAzureResourcesStep.class, steps.get(0).getClass());
    assertEquals(DeleteControlledAwsResourcesStep.class, steps.get(1).getClass());
    assertEquals(DeleteControlledSamResourcesStep.class, steps.get(2).getClass());
    assertEquals(DeleteGcpProjectStep.class, steps.get(3).getClass());
    assertEquals(EnsureNoWorkspaceChildrenStep.class, steps.get(4).getClass());
    assertEquals(DeleteAzureContextStep.class, steps.get(5).getClass());
    assertEquals(DeleteAwsContextStep.class, steps.get(6).getClass());
    assertEquals(DeleteWorkspaceAuthzStep.class, steps.get(7).getClass());
    assertEquals(DeleteWorkspaceStateStep.class, steps.get(8).getClass());
  }
}
