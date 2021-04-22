package bio.terra.workspace.service.resource.controlled.flight.create;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.bigquery.model.Dataset.Access;
import com.google.api.services.bigquery.model.DatasetReference;
import com.google.cloud.Binding;
import com.google.cloud.Policy;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for creating a BigQuery dataset cloud object.
 *
 * <p>Unlike other steps which create controlled resources, this step also syncs Sam policy groups
 * to cloud permissions on the new object. This is because BigQuery will default to granting legacy
 * BQ roles to project readers, editors, and owners if no IAM policy is specified at creation time.
 */
public class CreateBigQueryDatasetStep implements Step {

  private final CrlService crlService;
  private final ControlledBigQueryDatasetResource resource;

  private final Logger logger = LoggerFactory.getLogger(CreateBigQueryDatasetStep.class);

  public CreateBigQueryDatasetStep(
      CrlService crlService, ControlledBigQueryDatasetResource resource) {
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    FlightMap workingMap = flightContext.getWorkingMap();
    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        inputMap.get(CREATION_PARAMETERS, ApiGcpBigQueryDatasetCreationParameters.class);

    List<Access> iamConfiguration = buildDatasetIamConfiguration(workingMap);
    DatasetReference datasetId =
        new DatasetReference()
            .setProjectId(resource.getProjectId())
            .setDatasetId(resource.getDatasetName());
    Dataset datasetToCreate =
        new Dataset()
            .setDatasetReference(datasetId)
            .setLocation(creationParameters.getLocation())
            .setAccess(iamConfiguration);

    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    try {
      bqCow.datasets().insert(resource.getProjectId(), datasetToCreate).execute();
    } catch (GoogleJsonResponseException e) {
      // Stairway steps may run multiple times, so we may already have created this resource. In all
      // other cases, surface the exception and attempt to retry.
      if (e.getStatusCode() == HttpStatus.SC_CONFLICT) {
        logger.debug(
            "BQ dataset {} in project {} already exists",
            resource.getDatasetName(),
            resource.getProjectId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  /**
   * Builds the IAM configuration to grant workspace users access to the new dataset.
   *
   * <p>Unlike other cloud objects, BQ datasets do not use GCP's common IAM objects. Instead, they
   * use Access objects, which are roughly equivalent to the Bindings used elsewhere. With some
   * translation, that means this can still use the resource-type-agnostic policy-building code from
   * {@link GcpPolicyBuilder}.
   */
  private List<Access> buildDatasetIamConfiguration(FlightMap workingMap) {
    // As this is a new dataset, we pass an empty Policy object as the initial state to
    // GcpPolicyBuilder.
    GcpPolicyBuilder policyBuilder =
        new GcpPolicyBuilder(resource, resource.getProjectId(), Policy.newBuilder().build());

    // Read Sam groups for each workspace role. Stairway does not
    // have a cleaner way of deserializing parameterized types, so we suppress warnings here.
    @SuppressWarnings("unchecked")
    Map<WsmIamRole, String> workspaceRoleGroupsMap =
        workingMap.get(WorkspaceFlightMapKeys.IAM_GROUP_EMAIL_MAP, Map.class);
    for (Map.Entry<WsmIamRole, String> entry : workspaceRoleGroupsMap.entrySet()) {
      policyBuilder.addWorkspaceBinding(entry.getKey(), entry.getValue());
    }

    // Resources with permissions given to individual users (private or application managed) use
    // the resource's Sam policies to manage those individuals, so they must be synced here.
    // This section should also run for application managed resources, once those are supported.
    if (resource.getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
      @SuppressWarnings("unchecked")
      Map<ControlledResourceIamRole, String> resourceRoleGroupsMap =
          workingMap.get(ControlledResourceKeys.IAM_RESOURCE_GROUP_EMAIL_MAP, Map.class);
      for (Map.Entry<ControlledResourceIamRole, String> entry : resourceRoleGroupsMap.entrySet()) {
        policyBuilder.addResourceBinding(entry.getKey(), entry.getValue());
      }
    }

    Policy updatedPolicy = policyBuilder.build();
    List<Binding> bindingList = updatedPolicy.getBindingsList();
    return bindingList.stream().map(this::toAccess).collect(Collectors.toList());
  }

  /**
   * Translate a Binding object to an Access object.
   *
   * <p>Binding objects are a common IAM construct used across many GCP libraries. They represent a
   * single GCP role applied to one or more members.
   *
   * <p>Access objects are a BigQuery-specific IAM construct. They represent a single GCP role
   * applied to a single member. For legacy reasons, BigQuery datasets use lists of Access objects
   * instead of lists of Binding objects for IAM control.
   *
   * <p>This translation assumes that the provided Binding object only has a single member. This
   * holds for all Bindings built by a GcpPolicyBuilder object.
   */
  private Access toAccess(Binding binding) {
    Preconditions.checkArgument(
        binding.getMembers().size() == 1,
        "Cannot build an Access object from a Binding without exactly one member.");
    return new Access().setIamMember(binding.getMembers().get(0)).setRole(binding.getRole());
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();

    try {
      // With deleteContents set to true, this will delete the dataset even if other steps fail
      // to clean up tables or data.
      bqCow
          .datasets()
          .delete(resource.getProjectId(), resource.getDatasetName())
          .setDeleteContents(true)
          .execute();
    } catch (GoogleJsonResponseException e) {
      // Stairway steps may run multiple times, so we may already have deleted this resource.
      if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        logger.debug(
            "BQ dataset {} in project {} already deleted",
            resource.getDatasetName(),
            resource.getProjectId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }
}
