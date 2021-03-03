package bio.terra.workspace.service.resource.reference.flight.create;

import bio.terra.cloudres.google.bigquery.DatasetCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.reference.exception.InvalidReferenceException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.reference.ReferenceBigQueryDatasetResource;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.DatasetId;

public class CreateReferenceVerifyAccessBigQueryStep implements Step {
    private final CrlService crlService;

    public CreateReferenceVerifyAccessBigQueryStep(CrlService crlService) {
        this.crlService = crlService;
    }

    @Override
    public StepResult doStep(FlightContext flightContext) throws InterruptedException, RetryException {
        FlightMap inputMap = flightContext.getInputParameters();

        AuthenticatedUserRequest userReq =
                inputMap.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
        ReferenceBigQueryDatasetResource referenceResource =
                inputMap.get(JobMapKeys.REQUEST.getKeyName(), ReferenceBigQueryDatasetResource.class);
        String projectId = referenceResource.getAttributes().getProjectId();
        String datasetName = referenceResource.getAttributes().getDatasetName();

        try {
            DatasetId datasetId = DatasetId.of(projectId, datasetName);
            // BigQueryCow.get() returns null if the bucket does not exist or a user does not have access,
            // which fails validation.
            DatasetCow dataset = crlService.createBigQueryCow(userReq).getDataset(datasetId);
            if (dataset == null) {
                throw new InvalidReferenceException(
                        String.format(
                                "Could not access BigQuery dataset %s in project %s. Ensure the name and GCP project are correct and that you have access.",
                                datasetName, projectId));
            }
        } catch (BigQueryException e) {
            throw new InvalidReferenceException("Error while trying to access BigQuery dataset", e);
        }

        return StepResult.getStepResultSuccess();
    }

    @Override
    public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
        return StepResult.getStepResultSuccess();
    }
}
