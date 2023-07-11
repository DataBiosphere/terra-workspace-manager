package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.service.crl.CrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class DeleteAzureDiskStepTest extends BaseAzureUnitTest {
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private CrlService mockCrlService;
  @Mock private ControlledAzureDiskResource mockDiskResource;
  @Mock private FlightContext mockFlightContext;

  private DeleteAzureDiskStep testStep;

  @BeforeEach
  void setup() {
    testStep = new DeleteAzureDiskStep(mockAzureConfig, mockCrlService, mockDiskResource);
  }

  @Test
  void doStepWhenDiskSuccessfullyDeleted() throws InterruptedException {
    StepResult result = testStep.doStep(mockFlightContext);
    assertThat(result, equalTo(StepResult.getStepResultSuccess()));
  }

  //    @Test
  //    void doStepWhenDiskAttachedToVm() {
  //
  //    }
  //
  //    @Test
  //    void undoStep() {
  //
  //    }
}
