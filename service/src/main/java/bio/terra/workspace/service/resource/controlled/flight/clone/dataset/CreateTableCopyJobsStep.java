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
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.Job;
import com.google.api.services.bigquery.model.JobConfiguration;
import com.google.api.services.bigquery.model.JobConfigurationTableCopy;
import com.google.api.services.bigquery.model.JobList;
import com.google.api.services.bigquery.model.JobReference;
import com.google.api.services.bigquery.model.TableList;
import com.google.api.services.bigquery.model.TableList.Tables;
import com.google.api.services.bigquery.model.TableReference;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateTableCopyJobsStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateTableCopyJobsStep.class);
  public static final Duration COPY_JOB_TIMEOUT = Duration.ofHours(12);
  private final CrlService crlService;

  public CreateTableCopyJobsStep(CrlService crlService) {
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
    final AuthenticatedUserRequest userRequest =
        flightContext
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    final BigQueryCow bigQueryCow = crlService.createBigQueryCow(userRequest);
    // TODO:  remove usage of this client when it's all in CRL
    final Bigquery bigQueryClient = crlService.createNakedBigQueryClient(userRequest);
    try {
      // Get a list of all tables in the source dataset
      final TableList sourceTables =
          bigQueryCow
              .tables()
              .list(sourceInputs.getProjectId(), sourceInputs.getDatasetName())
              .execute();
      // Start a copy job for each source table
      //      final List<JobReference> submittedJobs = new ArrayList<>();
      @SuppressWarnings("unchecked")
      final Map<String, String> tableToJobId =
          Optional.ofNullable(
                  workingMap.get(
                      ControlledResourceKeys.TABLE_TO_JOB_ID_MAP,
                      Map.class)) // todo: use typereference
              .orElseGet(HashMap::new);
      for (TableList.Tables table : sourceTables.getTables()) {
        if (tableToJobId.containsKey(table.getId())) {
          // A job already exists for this table
          continue;
        }
        final Job inputJob = buildTableCopyJob(sourceInputs, destinationInputs, table);
        // bill the job to the source project
        final Job submittedJob =
            bigQueryClient.jobs().insert(sourceInputs.getProjectId(), inputJob).execute();

        final JobReference submittedJobReference = submittedJob.getJobReference();
        //        submittedJobs.add(submittedJobReference);
        tableToJobId.put(table.getId(), submittedJob.getId());
        workingMap.put(ControlledResourceKeys.TABLE_TO_JOB_ID_MAP, tableToJobId);

        //        final Jobs jobs = bigQueryClient.jobs();
        //        final JobList allJobs = jobs.list(submittedJobReference.getProjectId()).execute();
        //        allJobs.getJobs().forEach(j -> logger.info("Found job id {}", j.getId()));

        //        final Job gotJob =
        //            jobs.get(submittedJobReference.getProjectId(),
        // submittedJobReference.getJobId())
        //                .setLocation(submittedJobReference.getLocation())
        //                .execute();
        // job state string is undocumented, but assume it corresponds to
        // https://googleapis.dev/java/google-cloud-bigquery/latest/com/google/cloud/bigquery/JobStatus.State.html
      }

      // TODO: move to next step?
      // wait for all jobs to be DONE
      long totalChecks = COPY_JOB_TIMEOUT.toSeconds() / 10;
      while (totalChecks-- > 0) {
        final JobList allJobs = bigQueryClient.jobs().list(sourceInputs.getProjectId()).execute();
        final long numJobs = allJobs.getJobs().size();
        final long numPending =
            allJobs.getJobs().stream().filter(j -> j.getState().equals("PENDING")).count();
        final long numRunning =
            allJobs.getJobs().stream().filter(j -> j.getState().equals("RUNNING")).count();
        final long numDone =
            allJobs.getJobs().stream().filter(j -> j.getState().equals("DONE")).count();
        logger.info(
            "Copying {} tables: {} PENDING, {} RUNNING, {} DONE.",
            numJobs,
            numPending,
            numRunning,
            numDone);
        if (numJobs == numDone) {
          break;
        }
        TimeUnit.SECONDS.sleep(10);
      }
      if (totalChecks == 0) {
        return new StepResult(
            StepStatus.STEP_RESULT_FAILURE_RETRY,
            new RuntimeException("Jobs did not complete after 10000 seconds"));
      }
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo here because the whole dataset will be deleted in the undo path for
  // an earlier step.
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private Job buildTableCopyJob(
      DatasetCloneInputs sourceInputs, DatasetCloneInputs destinationInputs, Tables table) {
    final JobConfigurationTableCopy jobConfigurationTableCopy = new JobConfigurationTableCopy();
    // The source and destination table have the same table type.
    jobConfigurationTableCopy.setOperationType("COPY");
    // make new tables in empty destination dataset
    jobConfigurationTableCopy.setCreateDisposition("CREATE_IF_NEEDED");

    // replace contents on retry since appending will leave duplicate data
    jobConfigurationTableCopy.setWriteDisposition("WRITE_TRUNCATE");

    final TableReference sourceTableReference = buildTableReference(sourceInputs, table);
    jobConfigurationTableCopy.setSourceTable(sourceTableReference);

    final TableReference destinationTableReference = buildTableReference(destinationInputs, table);
    jobConfigurationTableCopy.setDestinationTable(destinationTableReference);

    final JobConfiguration jobConfiguration = new JobConfiguration();
    jobConfiguration.setJobType("COPY");
    jobConfiguration.setCopy(jobConfigurationTableCopy);
    jobConfiguration.setJobTimeoutMs(COPY_JOB_TIMEOUT.toMillis());

    final Job inputJob = new Job();
    inputJob.setConfiguration(jobConfiguration);
    return inputJob;
  }

  // Extract the table ID/name portion of an ID in the form project-id:dataset_name.tableId
  public static String getTableName(String fqTableId) {
    // Since neither the project nor the dataset can contain periods, we can simply split on
    // the period character
    final String[] parts = fqTableId.split("\\.");
    return parts[1];
  }

  private TableReference buildTableReference(DatasetCloneInputs sourceInputs, Tables table) {
    final TableReference sourceTableReference = new TableReference();
    sourceTableReference.setProjectId(sourceInputs.getProjectId());
    sourceTableReference.setDatasetId(sourceInputs.getDatasetName());
    sourceTableReference.setTableId(getTableName(table.getId()));
    return sourceTableReference;
  }
}
