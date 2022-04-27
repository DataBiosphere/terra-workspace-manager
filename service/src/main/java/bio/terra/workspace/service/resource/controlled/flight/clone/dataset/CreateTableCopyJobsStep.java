package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.Job;
import com.google.api.services.bigquery.model.JobConfiguration;
import com.google.api.services.bigquery.model.JobConfigurationTableCopy;
import com.google.api.services.bigquery.model.Table;
import com.google.api.services.bigquery.model.TableList;
import com.google.api.services.bigquery.model.TableList.Tables;
import com.google.api.services.bigquery.model.TableReference;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateTableCopyJobsStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateTableCopyJobsStep.class);
  public static final Duration COPY_JOB_TIMEOUT = Duration.ofHours(12);
  private final CrlService crlService;
  private final GcpCloudContextService gcpCloudContextService;
  private final ControlledBigQueryDatasetResource sourceDataset;

  public CreateTableCopyJobsStep(
      CrlService crlService,
      GcpCloudContextService gcpCloudContextService,
      ControlledBigQueryDatasetResource sourceDataset) {
    this.crlService = crlService;
    this.gcpCloudContextService = gcpCloudContextService;
    this.sourceDataset = sourceDataset;
  }

  /**
   * Create one BigQuery copy job for each table in the source dataset. Keep a running map from
   * table ID to job ID as new jobs are created, and only create jobs for tables that aren't in the
   * map already. Rerun the step after every table is processed so that the map may be persisted
   * incrementally.
   *
   * <p>On retry, create the jobs for any tables that don't have them. Use WRITE_TRUNCATE to avoid
   * the possibility of duplicate data.
   */
  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final CloningInstructions effectiveCloningInstructions =
        flightContext
            .getInputParameters()
            .get(ControlledResourceKeys.CLONING_INSTRUCTIONS, CloningInstructions.class);
    if (CloningInstructions.COPY_RESOURCE != effectiveCloningInstructions) {
      return StepResult.getStepResultSuccess();
    }

    // Gather inputs
    final DatasetCloneInputs sourceInputs = getSourceInputs();
    workingMap.put(ControlledResourceKeys.SOURCE_CLONE_INPUTS, sourceInputs);

    final DatasetCloneInputs destinationInputs = getDestinationInputs(flightContext);
    workingMap.put(ControlledResourceKeys.DESTINATION_CLONE_INPUTS, destinationInputs);

    final BigQueryCow bigQueryCow = crlService.createWsmSaBigQueryCow();
    // TODO(jaycarlton):  remove usage of this client when it's all in CRL PF-942
    final Bigquery bigQueryClient = crlService.createWsmSaNakedBigQueryClient();
    try {
      // Get a list of all tables in the source dataset
      final TableList sourceTables =
          bigQueryCow
              .tables()
              .list(sourceInputs.getProjectId(), sourceInputs.getDatasetName())
              .execute();
      // Start a copy job for each source table
      final Map<String, String> tableToJobId =
          Optional.ofNullable(
                  workingMap.get(
                      ControlledResourceKeys.TABLE_TO_JOB_ID_MAP,
                      new TypeReference<Map<String, String>>() {}))
              .orElseGet(HashMap::new);
      final List<Tables> tables =
          Optional.ofNullable(sourceTables.getTables()).orElse(Collections.emptyList());
      // Find the first table whose ID isn't a key in the map.
      final Optional<Tables> tableMaybe =
          tables.stream()
              .filter(t -> null != t.getId() && !tableToJobId.containsKey(t.getId()))
              .findFirst();
      if (tableMaybe.isPresent()) {
        final Tables table = tableMaybe.get();
        checkStreamingBuffer(sourceInputs, bigQueryCow, table);
        final Job inputJob = buildTableCopyJob(sourceInputs, destinationInputs, table);
        // bill the job to the destination project
        final Job submittedJob =
            bigQueryClient.jobs().insert(destinationInputs.getProjectId(), inputJob).execute();

        // Update the map, which will be persisted
        tableToJobId.put(table.getId(), submittedJob.getId());
        workingMap.put(ControlledResourceKeys.TABLE_TO_JOB_ID_MAP, tableToJobId);
        return new StepResult(StepStatus.STEP_RESULT_RERUN);
      } else {
        // All tables have entries in the map, so all jobs are started.
        workingMap.put(
            ControlledResourceKeys.TABLE_TO_JOB_ID_MAP, tableToJobId); // in case it's empty
        return StepResult.getStepResultSuccess();
      }
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }

  // Nothing to undo here because the whole dataset will be deleted in the undo path for
  // an earlier step.
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  // If the table contains data in its streaming buffer, that data can't be copied yet
  // https://cloud.google.com/bigquery/streaming-data-into-bigquery#dataavailability.
  // For now, we simply warn in the log if there's data that will be skipped.
  private void checkStreamingBuffer(
      DatasetCloneInputs sourceInputs, BigQueryCow bigQueryCow, Tables table) throws IOException {
    final Table tableGetResponse =
        bigQueryCow
            .tables()
            .get(
                sourceInputs.getProjectId(),
                sourceInputs.getDatasetName(),
                getTableName(table.getId()))
            .execute();
    if (tableGetResponse.getStreamingBuffer() != null) {
      // This is unfortunate, but our contract is to clone whatever BigQuery copies, which
      // doesn't
      // include recent streaming-inserted data. Warn in the log to assist in investigating
      // customer complaints.
      logger.warn(
          "Streaming buffer data in table {} will not be copied. Estimated rows: {}, Oldest entry time: {}",
          table.getId(),
          tableGetResponse.getStreamingBuffer().getEstimatedRows(),
          Instant.ofEpochMilli(
                  tableGetResponse.getStreamingBuffer().getOldestEntryTime().longValueExact())
              .toString());
    }
  }

  private static Job buildTableCopyJob(
      DatasetCloneInputs sourceInputs, DatasetCloneInputs destinationInputs, Tables table) {
    final JobConfigurationTableCopy jobConfigurationTableCopy = new JobConfigurationTableCopy();
    // The source and destination table have the same table type.
    jobConfigurationTableCopy.setOperationType("COPY");
    // make new tables in empty destination dataset
    jobConfigurationTableCopy.setCreateDisposition("CREATE_IF_NEEDED");

    // replace contents on retry since appending will leave duplicate data
    jobConfigurationTableCopy.setWriteDisposition("WRITE_TRUNCATE");

    jobConfigurationTableCopy.setSourceTable(buildTableReference(sourceInputs, table));
    jobConfigurationTableCopy.setDestinationTable(buildTableReference(destinationInputs, table));

    final JobConfiguration jobConfiguration = new JobConfiguration();
    jobConfiguration.setJobType("COPY");
    jobConfiguration.setCopy(jobConfigurationTableCopy);
    jobConfiguration.setJobTimeoutMs(COPY_JOB_TIMEOUT.toMillis());

    final Job inputJob = new Job();
    inputJob.setConfiguration(jobConfiguration);
    return inputJob;
  }

  // Extract the table ID/name portion of an ID in the form project-id:dataset_name.tableId
  private static String getTableName(String fqTableId) {
    // Since neither the project nor the dataset can contain periods, we can simply split on
    // the period character
    final String[] parts = fqTableId.split("\\.");
    return parts[1];
  }

  private static TableReference buildTableReference(DatasetCloneInputs inputs, Tables table) {
    final TableReference sourceTableReference = new TableReference();
    sourceTableReference.setProjectId(inputs.getProjectId());
    sourceTableReference.setDatasetId(inputs.getDatasetName());
    sourceTableReference.setTableId(getTableName(table.getId()));
    return sourceTableReference;
  }

  private DatasetCloneInputs getSourceInputs() {
    final String sourceProjectId =
        gcpCloudContextService.getRequiredGcpProject(sourceDataset.getWorkspaceId());
    final String sourceDatasetName = sourceDataset.getDatasetName();
    return new DatasetCloneInputs(
        sourceDataset.getWorkspaceId(), sourceProjectId, sourceDatasetName);
  }

  private DatasetCloneInputs getDestinationInputs(FlightContext flightContext) {
    final UUID destinationWorkspaceId =
        flightContext
            .getInputParameters()
            .get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    final String destinationProjectId =
        gcpCloudContextService.getRequiredGcpProject(destinationWorkspaceId);
    final String destinationDatasetName =
        flightContext
            .getWorkingMap()
            .get(ControlledResourceKeys.DESTINATION_DATASET_NAME, String.class);
    return new DatasetCloneInputs(
        destinationWorkspaceId, destinationProjectId, destinationDatasetName);
  }
}
