package bio.terra.workspace.service.workspace.flight;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.app.configuration.external.WorkspaceProjectConfiguration;
import bio.terra.workspace.db.WorkspaceDao;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

public class CreateGoogleContextFlight extends Flight {

  public CreateGoogleContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    ApplicationContext appContext = (ApplicationContext) applicationContext;
    WorkspaceProjectConfiguration workspaceProjectConfiguration =
        appContext.getBean(WorkspaceProjectConfiguration.class);
    CloudResourceManagerCow resourceManager = appContext.getBean(CloudResourceManagerCow.class);
    ServiceUsageCow serviceUsage = appContext.getBean(ServiceUsageCow.class);
    WorkspaceDao workspaceDao = appContext.getBean(WorkspaceDao.class);
    TransactionTemplate transactionTemplate = appContext.getBean(TransactionTemplate.class);

    addStep(new GenerateProjectIdStep());
    addStep(new CreateProjectStep(resourceManager, serviceUsage, workspaceProjectConfiguration));
    addStep(new StoreGoogleContextStep(workspaceDao, transactionTemplate));
  }
}
