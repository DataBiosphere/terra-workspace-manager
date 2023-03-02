package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.Job;
import com.google.api.services.bigquery.model.JobReference;
import com.google.api.services.bigquery.model.TableReference;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompleteTableCopyJobsStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CompleteTableCopyJobsStep.class);
  private final CrlService crlService;

  public CompleteTableCopyJobsStep(CrlService crlService) {
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final CloningInstructions effectiveCloningInstructions =
        flightContext
            .getInputParameters()
            .get(
                WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS,
                CloningInstructions.class);
    if (CloningInstructions.COPY_RESOURCE != effectiveCloningInstructions) {
      return StepResult.getStepResultSuccess();
    }
    final Map<String, String> tableToJobId =
        workingMap.get(ControlledResourceKeys.TABLE_TO_JOB_ID_MAP, new TypeReference<>() {});

    // TODO(jaycarlton): PF-942 implement needed endpoints in CRL and use them here
    final Bigquery bigQueryClient = crlService.createWsmSaNakedBigQueryClient();
    try {
      for (Map.Entry<String, String> entry : tableToJobId.entrySet()) {
        final TableReference tableReference = tableFqIdToReference(entry.getKey());
        final JobReference jobReference = jobFqIdToReference(entry.getValue());

        // wait for job to complete
        int sleepTimeSeconds = 1;
        while (true) {
          final Job job =
              bigQueryClient
                  .jobs()
                  .get(jobReference.getProjectId(), jobReference.getJobId())
                  .setLocation(
                      jobReference.getLocation()) // returns NOT_FOUND unless location is specified
                  .execute();
          final String jobState = job.getStatus().getState();
          logger.debug("Table {} is {}", tableReference.getTableId(), jobState);
          if ("DONE".equals(jobState)) {
            // Job has finished, but may have failed depending on the error result
            if (null != job.getStatus().getErrorResult()) {
              final String errorMessage = job.getStatus().getErrorResult().getMessage();
              logger.warn("Job {} failed: {}", job.getId(), errorMessage);
              // Retrying this step won't help, since the jobs are already started.
              // We have to treat a table-level failure as fatal to the whole flight.
              return new StepResult(
                  StepStatus.STEP_RESULT_FAILURE_FATAL, new RuntimeException(errorMessage));
            }
            break;
          }
          TimeUnit.SECONDS.sleep(sleepTimeSeconds);
          sleepTimeSeconds = Math.min(2 * sleepTimeSeconds, 60);
        }
      }
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo here because the whole dataset will be deleted in the undo path for
  // an earlier step.
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  // Fully-qualified table IDs are of the form project-id:dataset_id.table_id
  private TableReference tableFqIdToReference(String tableFqId) {
    final TableReference result = new TableReference();
    final String[] outerGroups = tableFqId.split(":");
    result.setProjectId(outerGroups[0]);

    final String[] innerGroups = outerGroups[1].split("\\.");
    result.setDatasetId(innerGroups[0]);
    result.setTableId(innerGroups[1]);
    return result;
  }

  private JobReference jobFqIdToReference(String jobFqId) {
    final JobReference result = new JobReference();
    final String[] outerGroups = jobFqId.split(":");
    result.setProjectId(outerGroups[0]);
    final String[] innerGroups = outerGroups[1].split("\\.");
    result.setLocation(innerGroups[0]);
    result.setJobId(innerGroups[1]);
    return result;
  }
}
