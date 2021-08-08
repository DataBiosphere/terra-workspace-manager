package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

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
    @SuppressWarnings("unchecked")
    final Map<String, String> tableToJobId =
        workingMap.get(ControlledResourceKeys.TABLE_TO_JOB_ID_MAP, Map.class);
    final AuthenticatedUserRequest userRequest =
        flightContext
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    final Bigquery bigQueryClient = crlService.createNakedBigQueryClient(userRequest);
    try {
      for (Map.Entry<String, String> entry : tableToJobId.entrySet()) {
        final String tableFqId = entry.getKey();
        logger.info("Waiting for table {}", tableFqId);
        final String jobFqId = entry.getValue();
        // wait for job to complete
        while (true) {
          final TableReference tableReference = tableFqIdToReference(tableFqId);
          final JobReference jobReference = jobFqIdToReference(jobFqId);
          final Job job =
              bigQueryClient
                  .jobs()
                  .get(jobReference.getProjectId(), jobReference.getJobId())
                  .setLocation(jobReference.getLocation())
                  .execute();
          final String jobState = job.getStatus().getState();
          logger.info("\tTable {} is {}", tableReference.getTableId(), jobState);
          if ("DONE".equals(jobState)) {
            break;
          }
          TimeUnit.SECONDS.sleep(10);
        }
      }
    } catch (IOException | InterruptedException e) {
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

    //    final Matcher matcher = TABLE_FQ_ID_PATTERN.matcher(tableFqId);
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
