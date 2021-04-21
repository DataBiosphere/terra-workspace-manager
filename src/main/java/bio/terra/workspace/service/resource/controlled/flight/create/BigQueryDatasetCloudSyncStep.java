package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.cloudres.google.api.services.common.Defaults;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.BigqueryScopes;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.bigquery.model.Dataset.Access;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.Binding;
import com.google.cloud.Policy;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BigQueryDatasetCloudSyncStep implements Step {

  private final CrlService crlService;
  private final ControlledBigQueryDatasetResource resource;
  private final Logger logger = LoggerFactory.getLogger(BigQueryDatasetCloudSyncStep.class);

  public BigQueryDatasetCloudSyncStep(
      CrlService crlService, ControlledBigQueryDatasetResource resource) {
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    // Unlike other cloud objects, BQ datasets do not use the pattern of
    // getIamPolicy()/setIamPolicy(), and instead use a custom "Access" object-based interface.
    final FlightMap workingMap = flightContext.getWorkingMap();
    // TODO: experimentation pre-CRL modification
    try {
      Bigquery bq =
          new Bigquery.Builder(
                  Defaults.httpTransport(),
                  Defaults.jsonFactory(),
                  new HttpCredentialsAdapter(
                      GoogleCredentials.getApplicationDefault().createScoped(BigqueryScopes.all())))
              .setApplicationName("wsm trial")
              .build();
      Dataset dataset =
          bq.datasets().get(resource.getProjectId(), resource.getDatasetName()).execute();

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
        for (Map.Entry<ControlledResourceIamRole, String> entry :
            resourceRoleGroupsMap.entrySet()) {
          policyBuilder.addResourceBinding(entry.getKey(), entry.getValue());
        }
      }

      Policy updatedPolicy = policyBuilder.build();
      List<Access> updatedAccessList = accessFromBindings(updatedPolicy.getBindingsList());
      dataset.setAccess(updatedAccessList);
      logger.warn("About to request the following Access objects: {}", updatedAccessList);
      for (Access access : updatedAccessList) {
        logger.warn("That includes {}", access.getRole());
      }
      bq.datasets()
          .patch(resource.getProjectId(), dataset.getDatasetReference().getDatasetId(), dataset)
          .execute();
      return StepResult.getStepResultSuccess();
    } catch (GeneralSecurityException e) {
      logger.warn("General security exception: ", e);
      throw new RuntimeException();
    } catch (IOException e) {
      logger.warn("IOException: ", e);
      throw new RuntimeException();
    }
  }

  /**
   * Access objects are the legacy bigquery equivalent of Binding objects elsewhere in cloud
   * libraries: they represent a single GCP role assigned to a single member. This method translates
   * from Access objects to Binding objects, making the assumption that each Binding object only
   * applies to a single member.
   */
  private List<Binding> bindingsFromAccess(List<Access> accessList) {
    return accessList.stream()
        .map(
            access ->
                Binding.newBuilder()
                    .setRole(legacyRoleTranslation(access.getRole()))
                    .addMembers(memberFromAccess(access))
                    .build())
        .collect(Collectors.toList());
  }

  private String memberFromAccess(Access access) {
    String member =
        Optional.ofNullable(access.getGroupByEmail())
            .map(GcpPolicyBuilder::toMemberIdentifier)
            .orElse(access.getIamMember());
    logger.warn("Returning member {} from access {}", member, access);
    return member;
  }

  /** TODO: doc */
  private String legacyRoleTranslation(String role) {
    if (role.equals("READER")) {
      return "roles/bigquery.dataViewer";
    } else if (role.equals("WRITER")) {
      return "roles/bigquery.dataEditor";
    } else if (role.equals("OWNER")) {
      return "roles/bigquery.dataOwner";
    } else {
      return role;
    }
  }

  /** TODO: better doc. */
  private List<Access> accessFromBindings(List<Binding> bindingList) {
    return bindingList.stream()
        .map(
            binding ->
                new Access().setIamMember(binding.getMembers().get(0)).setRole(binding.getRole()))
        .collect(Collectors.toList());
  }

  /**
   * Because the resource will be deleted when other steps are undone, we don't need to undo
   * permissions.
   */
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
