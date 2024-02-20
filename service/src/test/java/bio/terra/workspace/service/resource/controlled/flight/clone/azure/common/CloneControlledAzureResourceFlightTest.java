package bio.terra.workspace.service.resource.controlled.flight.clone.azure.common;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseSpringBootAzureUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.CheckControlledResourceAuthStep;
import bio.terra.workspace.service.resource.controlled.flight.create.GetAzureCloudContextStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.model.StepRetryRulePair;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.mockito.Mock;

@Tag("azureUnit")
public class CloneControlledAzureResourceFlightTest extends BaseSpringBootAzureUnitTest {

  @Mock private FlightBeanBag flightBeanBag;
  @Mock private AuthenticatedUserRequest userRequest;

  static UUID sourceWorkspaceId = UUID.randomUUID();
  static UUID destinationWorkspaceId = UUID.randomUUID();
  static UUID destinationResourceId = UUID.randomUUID();
  static ControlledAzureManagedIdentityResource sourceResource =
      ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
              ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters(),
              sourceWorkspaceId)
          .managedIdentityName("idfoobar")
          .build();

  @Test
  void cloneAzureResource_verifyInputs() {
    FlightMap inputs = new FlightMap();
    assertThrows(
        MissingRequiredFieldsException.class,
        () -> new DefaultCloneAzureResourceFlight(inputs, flightBeanBag));

    inputs.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, sourceResource);
    assertThrows(
        MissingRequiredFieldsException.class,
        () -> new DefaultCloneAzureResourceFlight(inputs, flightBeanBag));

    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    assertThrows(
        MissingRequiredFieldsException.class,
        () -> new DefaultCloneAzureResourceFlight(inputs, flightBeanBag));

    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID,
        destinationResourceId);
    assertThrows(
        MissingRequiredFieldsException.class,
        () -> new DefaultCloneAzureResourceFlight(inputs, flightBeanBag));

    inputs.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME, sourceResource.getName());
    assertThrows(
        MissingRequiredFieldsException.class,
        () -> new DefaultCloneAzureResourceFlight(inputs, flightBeanBag));

    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        destinationWorkspaceId);
    // Cloning instructions are optional, they will be pulled from source resource (in this case
    // COPY_NOTHING)
    assertDoesNotThrow(() -> new DefaultCloneAzureResourceFlight(inputs, flightBeanBag));
  }

  FlightMap inputsWithCloneInstruction(CloningInstructions instructions) {
    FlightMap inputs = new FlightMap();
    inputs.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, sourceResource);
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID,
        destinationResourceId);
    inputs.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME, sourceResource.getName());
    inputs.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        destinationWorkspaceId);
    inputs.put(WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS, instructions);
    return inputs;
  }

  @Test
  void cloneResource_copyNothingSteps() {
    var copyNothingFlight =
        new DefaultCloneAzureResourceFlight(
            inputsWithCloneInstruction(CloningInstructions.COPY_NOTHING), flightBeanBag);
    var expectedSteps =
        new ArrayList<>(
            List.of(SetNoOpResourceCloneResourceStep.class, SetCloneFlightResponseStep.class));
    assertEquals(expectedSteps.size(), copyNothingFlight.getSteps().size());
    assertEquals(
        0L,
        copyNothingFlight.getSteps().stream()
            .dropWhile((step -> expectedSteps.remove(0).equals(step.getClass())))
            .count());
  }

  @Test
  void cloneResource_copyDefinitionSteps() {
    var inputs = inputsWithCloneInstruction(CloningInstructions.COPY_DEFINITION);
    assertThrows(
        IllegalArgumentException.class,
        () -> new DefaultCloneAzureResourceFlight(inputs, flightBeanBag));
    var copyDefinitionFlight = new DummyStepCloneAzureResourceFlight(inputs, flightBeanBag);
    var expectedSteps =
        new ArrayList<>(
            List.of(
                GetAzureCloudContextStep.class,
                CheckControlledResourceAuthStep.class,
                RetrieveControlledResourceMetadataStep.class,
                VerifyControlledResourceDoesNotExist.class,
                CopyDefinitionStep.class,
                SetCloneFlightResponseStep.class));
    assertEquals(expectedSteps.size(), copyDefinitionFlight.getSteps().size());
    assertEquals(
        0L,
        copyDefinitionFlight.getSteps().stream()
            .dropWhile((step -> expectedSteps.remove(0).equals(step.getClass())))
            .count());
  }

  @Test
  void cloneResource_copyResourceSteps() {
    var inputs = inputsWithCloneInstruction(CloningInstructions.COPY_RESOURCE);
    assertThrows(
        IllegalArgumentException.class,
        () -> new DefaultCloneAzureResourceFlight(inputs, flightBeanBag));
    var copyResourceFlight = new DummyStepCloneAzureResourceFlight(inputs, flightBeanBag);
    var expectedSteps =
        new ArrayList<>(
            List.of(
                GetAzureCloudContextStep.class,
                CheckControlledResourceAuthStep.class,
                RetrieveControlledResourceMetadataStep.class,
                VerifyControlledResourceDoesNotExist.class,
                CopyDefinitionStep.class,
                CopyResourceStep.class,
                SetCloneFlightResponseStep.class));
    assertEquals(copyResourceFlight.getSteps().size(), expectedSteps.size());
    assertEquals(
        0L,
        copyResourceFlight.getSteps().stream()
            .dropWhile((step -> expectedSteps.remove(0).equals(step.getClass())))
            .count());
  }

  @Test
  void cloneResource_copyReferenceSteps() {
    var inputs = inputsWithCloneInstruction(CloningInstructions.COPY_REFERENCE);
    assertThrows(
        IllegalArgumentException.class,
        () -> new DefaultCloneAzureResourceFlight(inputs, flightBeanBag));
    var copyReferenceFlight = new DummyStepCloneAzureResourceFlight(inputs, flightBeanBag);
    var expectedSteps =
        new ArrayList<>(
            List.of(
                GetAzureCloudContextStep.class,
                CheckControlledResourceAuthStep.class,
                RetrieveControlledResourceMetadataStep.class,
                VerifyControlledResourceDoesNotExist.class,
                CopyReferenceStep.class,
                SetCloneFlightResponseStep.class));
    assertEquals(copyReferenceFlight.getSteps().size(), expectedSteps.size());
    assertEquals(
        0L,
        copyReferenceFlight.getSteps().stream()
            .dropWhile((step -> expectedSteps.remove(0).equals(step.getClass())))
            .count());
  }

  @Test
  void cloneResource_linkReferenceSteps() {
    var inputs = inputsWithCloneInstruction(CloningInstructions.LINK_REFERENCE);
    assertThrows(
        IllegalArgumentException.class,
        () -> new DefaultCloneAzureResourceFlight(inputs, flightBeanBag));
    var linkReferenceFlight = new DummyStepCloneAzureResourceFlight(inputs, flightBeanBag);
    var expectedSteps =
        new ArrayList<>(
            List.of(
                GetAzureCloudContextStep.class,
                CheckControlledResourceAuthStep.class,
                RetrieveControlledResourceMetadataStep.class,
                VerifyControlledResourceDoesNotExist.class,
                LinkReferenceStep.class,
                SetCloneFlightResponseStep.class));
    assertEquals(linkReferenceFlight.getSteps().size(), expectedSteps.size());
    assertEquals(
        0L,
        linkReferenceFlight.getSteps().stream()
            .dropWhile((step -> expectedSteps.remove(0).equals(step.getClass())))
            .count());
  }

  class DefaultCloneAzureResourceFlight extends CloneControlledAzureResourceFlight {
    DefaultCloneAzureResourceFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
    }
  }

  class DummyStepCloneAzureResourceFlight extends CloneControlledAzureResourceFlight {
    DummyStepCloneAzureResourceFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
    }

    @Override
    protected List<StepRetryRulePair> copyDefinition(
        FlightBeanBag flightBeanBag, FlightMap inputParameters) {
      return List.of(
          new StepRetryRulePair(new CopyDefinitionStep(), RetryRuleNone.getRetryRuleNone()));
    }

    @Override
    protected List<StepRetryRulePair> copyResource(
        FlightBeanBag flightBeanBag, FlightMap inputParameters) {
      return List.of(
          new StepRetryRulePair(new CopyResourceStep(), RetryRuleNone.getRetryRuleNone()));
    }

    @Override
    protected List<StepRetryRulePair> copyReference(
        FlightBeanBag flightBeanBag, FlightMap inputParameters) {
      return List.of(
          new StepRetryRulePair(new CopyReferenceStep(), RetryRuleNone.getRetryRuleNone()));
    }

    @Override
    protected List<StepRetryRulePair> linkReference(
        FlightBeanBag flightBeanBag, FlightMap inputParameters) {
      return List.of(
          new StepRetryRulePair(new LinkReferenceStep(), RetryRuleNone.getRetryRuleNone()));
    }
  }

  abstract class DummyStep implements Step {

    @Override
    public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
      return null;
    }

    @Override
    public StepResult undoStep(FlightContext context) throws InterruptedException {
      return null;
    }
  }

  class CopyDefinitionStep extends DummyStep {}

  class CopyResourceStep extends DummyStep {}

  class CopyReferenceStep extends DummyStep {}

  class LinkReferenceStep extends DummyStep {}
}
