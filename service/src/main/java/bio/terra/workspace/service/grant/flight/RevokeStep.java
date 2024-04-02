package bio.terra.workspace.service.grant.flight;

import static bio.terra.workspace.service.grant.flight.RevokeTemporaryGrantFlight.SKIP;
import static java.lang.Boolean.TRUE;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.cloudres.google.dataproc.DataprocCow;
import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.common.exception.ConflictException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.db.GrantDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.grant.GrantData;
import bio.terra.workspace.service.petserviceaccount.PetSaUtils;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster.ControlledDataprocClusterResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.ControlledGceInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.bigquery.model.Dataset;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.api.services.cloudresourcemanager.v3.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import com.google.api.services.cloudresourcemanager.v3.model.SetIamPolicyRequest;
import com.google.api.services.compute.model.ZoneSetPolicyRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step 2: revoke the permission
 *
 * <p>A step that illustrates the inconsistent approach to IAM in GCP
 */
public class RevokeStep implements Step {
  public static final Logger logger = LoggerFactory.getLogger(RevokeStep.class);
  private final GcpCloudContextService gcpCloudContextService;
  private final CrlService crlService;
  private final GrantDao grantDao;
  private final ControlledResourceService controlledResourceService;
  private final UUID grantId;

  public RevokeStep(
      GcpCloudContextService gcpCloudContextService,
      CrlService crlService,
      GrantDao grantDao,
      ControlledResourceService controlledResourceService,
      UUID grantId) {
    this.gcpCloudContextService = gcpCloudContextService;
    this.crlService = crlService;
    this.grantDao = grantDao;
    this.controlledResourceService = controlledResourceService;
    this.grantId = grantId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    Boolean skip = context.getWorkingMap().get(SKIP, Boolean.class);
    if (TRUE.equals(skip)) {
      logger.debug("Skipping revoke of {}", grantId);
      return StepResult.getStepResultSuccess();
    }

    // Get the grant data - we locked it, so if it is not there something is wrong
    GrantData grantData = grantDao.getGrant(grantId);
    if (grantData == null) {
      throw new InternalLogicException("Locked grant not found: " + grantId);
    }

    try {
      switch (grantData.grantType()) {
        case RESOURCE -> revokeResource(grantData);
        case PROJECT -> revokeProject(grantData);
        case ACT_AS -> revokeActAs(grantData);
      }
    } catch (ConflictException | IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private void revokeProject(GrantData grantData) throws IOException {
    CloudResourceManagerCow resourceManagerCow = crlService.getCloudResourceManagerCow();
    Optional<String> gcpProjectId = gcpCloudContextService.getGcpProject(grantData.workspaceId());
    logger.info(
        "Revoking grant {} type {} workspace {} project {}",
        grantData.grantId(),
        grantData.grantType(),
        grantData.workspaceId(),
        gcpProjectId);

    // Tolerate the workspace or cloud context being gone
    if (gcpProjectId.isPresent()) {
      Policy policy =
          resourceManagerCow
              .projects()
              .getIamPolicy(gcpProjectId.get(), new GetIamPolicyRequest())
              .execute();
      // Tolerate no bindings
      if (policy.getBindings() != null) {
        for (Binding binding : policy.getBindings()) {
          if (binding.getRole().equals(grantData.role())) {
            if (grantData.userMember() != null) {
              binding.getMembers().remove(grantData.userMember());
            }
            binding.getMembers().remove(grantData.petSaMember());
          }
        }
        SetIamPolicyRequest request = new SetIamPolicyRequest().setPolicy(policy);
        resourceManagerCow.projects().setIamPolicy(gcpProjectId.get(), request).execute();
      }
    }
  }

  private void revokeResource(GrantData grantData) throws IOException {
    logger.info(
        "Revoking grant {} type {} workspace {} resource {}",
        grantData.grantId(),
        grantData.grantType(),
        grantData.workspaceId(),
        grantData.resourceId());

    ControlledResource controlledResource;
    try {
      controlledResource =
          controlledResourceService.getControlledResource(
              grantData.workspaceId(), grantData.resourceId());
    } catch (ResourceNotFoundException e) {
      logger.info("Resource {} not found; forgetting temporary grant", grantData.resourceId());
      return;
    }

    logger.info(
        "Found resource {} of type {}",
        controlledResource.getResourceId(),
        controlledResource.getResourceType());

    // If this were permanent, I would put the revoke logic in each controlled resource.
    // To keep it to a small number of files, we use a switch.
    switch (controlledResource.getResourceType()) {
      case CONTROLLED_GCP_GCS_BUCKET -> {
        ControlledGcsBucketResource bucketResource =
            controlledResource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
        revokeResourceBucket(bucketResource, grantData);
      }
      case CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE -> {
        ControlledAiNotebookInstanceResource notebookResource =
            controlledResource.castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
        revokeResourceNotebook(notebookResource, grantData);
      }
      case CONTROLLED_GCP_BIG_QUERY_DATASET -> {
        ControlledBigQueryDatasetResource bqResource =
            controlledResource.castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
        revokeResourceBq(bqResource, grantData);
      }
      case CONTROLLED_GCP_GCE_INSTANCE -> {
        ControlledGceInstanceResource gceInstanceResource =
            controlledResource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);
        revokeResourceGceInstance(gceInstanceResource, grantData);
      }
      case CONTROLLED_GCP_DATAPROC_CLUSTER -> {
        ControlledDataprocClusterResource dataprocClusterResource =
            controlledResource.castByEnum(WsmResourceType.CONTROLLED_GCP_DATAPROC_CLUSTER);
        removeResourceDataprocCluster(dataprocClusterResource, grantData);
      }
      default -> throw new InternalLogicException("Non-GCP resource got a temporary grant");
    }
  }

  private void revokeResourceBq(ControlledBigQueryDatasetResource bqResource, GrantData grantData)
      throws IOException {
    BigQueryCow bqCow = crlService.createWsmSaBigQueryCow();
    String gcpProjectId = bqResource.getProjectId();
    String datasetName = bqResource.getDatasetName();
    String petSaEmail = GcpUtils.fromSaMember(grantData.petSaMember());
    String userEmail =
        grantData.userMember() != null ? GcpUtils.fromUserMember(grantData.userMember()) : null;

    bqCow.datasets().get(gcpProjectId, datasetName);
    logger.debug("Revoke bqDataset {} in project {}", bqResource.getName(), gcpProjectId);

    Dataset dataset = CrlService.getBigQueryDataset(bqCow, gcpProjectId, datasetName);
    List<Dataset.Access> currentAccessList = dataset.getAccess();
    List<Dataset.Access> accessList = new ArrayList<>();
    for (Dataset.Access access : currentAccessList) {
      if (StringUtils.equals(access.getRole(), grantData.role())
          && access.getUserByEmail() != null) {
        if (StringUtils.equals(access.getUserByEmail(), petSaEmail)
            || StringUtils.equals(access.getUserByEmail(), userEmail)) {
          continue;
        }
      }
      accessList.add(access);
    }

    dataset.setAccess(accessList);
    crlService.updateBigQueryDataset(bqCow, gcpProjectId, datasetName, dataset);
  }

  private void revokeResourceBucket(
      ControlledGcsBucketResource bucketResource, GrantData grantData) {
    String gcpProjectId = gcpCloudContextService.getRequiredGcpProject(grantData.workspaceId());
    logger.debug("Revoke bucket {} in project {}", bucketResource.getName(), gcpProjectId);

    StorageCow wsmSaStorageCow = crlService.createStorageCow(gcpProjectId);
    com.google.cloud.Policy policy = wsmSaStorageCow.getIamPolicy(bucketResource.getBucketName());
    if (policy.getBindingsList() != null) {
      // getBindingsList() returns an ImmutableList and copying over to an ArrayList so it's
      // mutable.
      List<com.google.cloud.Binding> bindings = new ArrayList<>(policy.getBindingsList());
      // Remove role-member
      for (int index = 0; index < bindings.size(); index++) {
        com.google.cloud.Binding binding = bindings.get(index);
        if (binding.getRole().equals(grantData.role())) {
          com.google.cloud.Binding.Builder builder = binding.toBuilder();
          builder.removeMembers(grantData.petSaMember());
          if (grantData.userMember() != null) {
            builder.removeMembers(grantData.userMember());
          }
          bindings.set(index, builder.build());
          break;
        }
      }

      // Update policy to remove members
      com.google.cloud.Policy.Builder updatedPolicyBuilder = policy.toBuilder();
      updatedPolicyBuilder.setBindings(bindings).setVersion(3);
      com.google.cloud.Policy updatedPolicy =
          wsmSaStorageCow.setIamPolicy(
              bucketResource.getBucketName(), updatedPolicyBuilder.build());
    }
  }

  private void revokeResourceNotebook(
      ControlledAiNotebookInstanceResource notebookResource, GrantData grantData)
      throws IOException {
    logger.info(
        "Revoke notebook {} in project {}",
        notebookResource.getName(),
        notebookResource.getProjectId());

    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    InstanceName instanceName = notebookResource.toInstanceName(notebookResource.getLocation());

    com.google.api.services.notebooks.v1.model.Policy policy =
        notebooks.instances().getIamPolicy(instanceName).execute();
    List<com.google.api.services.notebooks.v1.model.Binding> bindings = policy.getBindings();

    if (bindings != null) {
      // Remove role-member
      for (com.google.api.services.notebooks.v1.model.Binding binding : bindings) {
        if (binding.getRole().equals(grantData.role())) {
          List<String> members = binding.getMembers();
          members.removeIf(
              member ->
                  member.equals(grantData.petSaMember()) || member.equals(grantData.userMember()));
          binding.setMembers(members);
        }
      }

      // Update policy to remove members
      notebooks
          .instances()
          .setIamPolicy(
              instanceName,
              new com.google.api.services.notebooks.v1.model.SetIamPolicyRequest()
                  .setPolicy(policy))
          .execute();
    }
  }

  private void revokeResourceGceInstance(
      ControlledGceInstanceResource gceInstanceResource, GrantData grantData) throws IOException {
    logger.info(
        "Revoke GCE Instance {} in project {}",
        gceInstanceResource.getName(),
        gceInstanceResource.getProjectId());

    CloudComputeCow cloudComputeCow = crlService.getCloudComputeCow();
    com.google.api.services.compute.model.Policy policy =
        cloudComputeCow
            .instances()
            .getIamPolicy(
                gceInstanceResource.getProjectId(),
                gceInstanceResource.getZone(),
                gceInstanceResource.getInstanceId())
            .execute();
    List<com.google.api.services.compute.model.Binding> bindings = policy.getBindings();

    if (bindings != null) {
      // Remove role-member
      for (com.google.api.services.compute.model.Binding binding : bindings) {
        if (binding.getRole().equals(grantData.role())) {
          List<String> members = binding.getMembers();
          members.removeIf(
              member ->
                  member.equals(grantData.petSaMember()) || member.equals(grantData.userMember()));
          binding.setMembers(members);
        }
      }
      // Update policy to remove members
      cloudComputeCow
          .instances()
          .setIamPolicy(
              gceInstanceResource.getProjectId(),
              gceInstanceResource.getZone(),
              gceInstanceResource.getInstanceId(),
              new ZoneSetPolicyRequest().setPolicy(policy))
          .execute();
    }
  }

  private void removeResourceDataprocCluster(
      ControlledDataprocClusterResource dataprocClusterResource, GrantData grantData)
      throws IOException {
    logger.info(
        "Revoke Dataproc Cluster {} in project {}",
        dataprocClusterResource.getName(),
        dataprocClusterResource.getProjectId());

    DataprocCow dataprocCow = crlService.getDataprocCow();
    com.google.api.services.dataproc.model.Policy policy =
        dataprocCow.clusters().getIamPolicy(dataprocClusterResource.toClusterName()).execute();
    List<com.google.api.services.dataproc.model.Binding> bindings = policy.getBindings();

    if (bindings != null) {
      // Remove role-member
      for (com.google.api.services.dataproc.model.Binding binding : bindings) {
        if (binding.getRole().equals(grantData.role())) {
          List<String> members = binding.getMembers();
          members.removeIf(
              member ->
                  member.equals(grantData.petSaMember()) || member.equals(grantData.userMember()));
          binding.setMembers(members);
        }
      }

      // Update policy to remove members
      dataprocCow
          .clusters()
          .setIamPolicy(
              dataprocClusterResource.toClusterName(),
              new com.google.api.services.dataproc.model.SetIamPolicyRequest().setPolicy(policy))
          .execute();
    }
  }

  private void revokeActAs(GrantData grantData) throws IOException {
    Optional<String> maybeProjectId = gcpCloudContextService.getGcpProject(grantData.workspaceId());
    if (maybeProjectId.isEmpty()) {
      // This GCP context has been deleted already, so there's nothing left to revoke.
      return;
    }
    String projectId = maybeProjectId.get();
    logger.info(
        "Revoking grant {} type {} workspace {} project {}",
        grantData.grantId(),
        grantData.grantType(),
        grantData.workspaceId(),
        projectId);

    try {
      String petSaEmail = GcpUtils.fromSaMember(grantData.petSaMember());
      ServiceAccountName saName =
          ServiceAccountName.builder().email(petSaEmail).projectId(projectId).build();

      com.google.api.services.iam.v1.model.Policy saPolicy =
          crlService.getIamCow().projects().serviceAccounts().getIamPolicy(saName).execute();

      // If the member is already not on the policy, we are done
      // This handles the case where there are no bindings at all, so we don't
      // need to worry about null binding later in the logic.
      boolean removedPet = PetSaUtils.removeSaMember(saPolicy, grantData.petSaMember());
      boolean removedUser =
          grantData.userMember() != null
              && PetSaUtils.removeSaMember(saPolicy, grantData.userMember());

      // If there was anything to remove, update the policy
      if (removedPet || removedUser) {
        com.google.api.services.iam.v1.model.SetIamPolicyRequest request =
            new com.google.api.services.iam.v1.model.SetIamPolicyRequest().setPolicy(saPolicy);
        crlService.getIamCow().projects().serviceAccounts().setIamPolicy(saName, request).execute();
      }
    } catch (IOException e) {
      if (e instanceof GoogleJsonResponseException g) {
        if (g.getStatusCode() == HttpStatus.SC_CONFLICT) {
          throw new ConflictException("Conflict revoking pet SA", e);
        }
      }
      throw e;
    }
  }
}
