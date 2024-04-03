package bio.terra.workspace.service.workspace.flight.application;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;

public class ApplicationAbleFlightV3 extends Flight {
  public ApplicationAbleFlightV3(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag beanBag = FlightBeanBag.getFromObject(applicationContext);

    // get data from inputs that steps need

    List<String> applicationIdList =
        inputParameters.get(WorkspaceFlightMapKeys.APPLICATION_IDS, new TypeReference<>() {});

    for (String applicationId : applicationIdList) {
      addStep(
          new ApplicationAblePrecheckStepV3(
              beanBag.getApplicationDao(), beanBag.getSamService(), applicationId));

      addStep(new ApplicationAbleIamStepV3(beanBag.getSamService()));

      addStep(new ApplicationAbleDaoStepV3(beanBag.getApplicationDao(), applicationId));
    }
  }
}
