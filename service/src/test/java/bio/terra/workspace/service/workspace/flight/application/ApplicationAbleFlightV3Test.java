package bio.terra.workspace.service.workspace.flight.application;


import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.stairway.StepUtils;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.mockito.Mockito.mock;

@Tag("unit")
class ApplicationAbleFlightV3Test {
  @Test
  void testDataFlow() {
    FlightMap inputParameters = new FlightMap();
    inputParameters.put(
            WorkspaceFlightMapKeys.APPLICATION_IDS,
            Arrays.asList("applicationId1"));
    Flight flight = new ApplicationAbleFlightV3(inputParameters, mock(FlightBeanBag.class));
    StepUtils.generateFlowAnalysisReport(flight).forEach(System.out::println);
  }
}