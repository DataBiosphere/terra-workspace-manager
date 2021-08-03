package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.services.bigquery.model.Job;
import com.google.api.services.bigquery.model.JobConfiguration;
import com.google.api.services.bigquery.model.JobConfigurationTableCopy;
import com.google.api.services.bigquery.model.TableList;
import com.google.api.services.bigquery.model.TableList.Tables;
import com.google.api.services.bigquery.model.TableReference;
import java.io.IOException;
import java.time.Duration;

public class CopyTablesStep implements Step {

  public static final Duration COPY_JOB_TIMEOUT = Duration.ofHours(12);
  private final CrlService crlService;

  public CopyTablesStep(CrlService crlService) {
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final DatasetCloneInputs sourceInputs =
        workingMap.get(ControlledResourceKeys.SOURCE_CLONE_INPUTS, DatasetCloneInputs.class);
    final DatasetCloneInputs destinationInputs =
        workingMap.get(ControlledResourceKeys.DESTINATION_CLONE_INPUTS, DatasetCloneInputs.class);
    final AuthenticatedUserRequest userRequest = flightContext.getInputParameters().get(JobMapKeys.AUTH_USER_INFO
        .getKeyName(), AuthenticatedUserRequest.class);
    final BigQueryCow bigQueryCow = crlService.createBigQueryCow(userRequest);
    try {
      // Get a list of all tables in the source dataset
      final TableList sourceTables = bigQueryCow.tables().list(sourceInputs.getProjectId(), sourceInputs.getDatasetId()).execute();
      // Start a copy job for each source table
      for (TableList.Tables table : sourceTables.getTables()) {
        final JobConfigurationTableCopy jobConfigurationTableCopy = new JobConfigurationTableCopy();
        // make new tables in empty destination dataset
        jobConfigurationTableCopy.setCreateDisposition("CREATE_IF_NEEDED");

        // replace contents on retry since appending will leave duplicate data
        jobConfigurationTableCopy.setWriteDisposition("WRITE_TRUNCATE");

        final TableReference sourceTableReference = buildTableReference(sourceInputs, table);
        jobConfigurationTableCopy.setSourceTable(sourceTableReference);

        final TableReference destinationTableReference = buildTableReference(destinationInputs,
            table);
        jobConfigurationTableCopy.setDestinationTable(destinationTableReference);

        final JobConfiguration jobConfiguration = new JobConfiguration();
        jobConfiguration.setJobType("COPY");
        jobConfiguration.setCopy(jobConfigurationTableCopy);
        jobConfiguration.setJobTimeoutMs(COPY_JOB_TIMEOUT.toMillis());

        final Job job = new Job();
        job.setConfiguration(jobConfiguration);
//        bigQueryCow.jobs().insert(job).execute();
      }
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
    return StepResult.getStepResultSuccess();
  }

  private TableReference buildTableReference(DatasetCloneInputs sourceInputs, Tables table) {
    final TableReference sourceTableReference = new TableReference();
    sourceTableReference.setProjectId(sourceInputs.getProjectId());
    sourceTableReference.setDatasetId(sourceInputs.getDatasetName());
    sourceTableReference.setTableId(table.getId());
    return sourceTableReference;
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return null;
  }
}
