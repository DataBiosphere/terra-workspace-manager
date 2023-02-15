package bio.terra.workspace.service.workspace;

import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.CustomGcpIamRole;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.List;

/**
 * This mapping describes the project-level GCP roles granted to members of a workspace.
 *
 * <p>Granting these roles at the project level was implemented as a temporary workaround to support
 * objects in a cloud context before controlled resources were built. As controlled resources become
 * available, roles should be granted directly on controlled resources instead (see {@code
 * CustomGcpIamRoleMapping}), and should be removed from this list. Some permissions must be granted
 * at the project level, and will continue to live here.
 *
 * <p>!!!If you change this file, if you want to backfill the change to existing projects, contact
 * admin to run syncIamRoles endpoint.!!!
 */
public class CloudSyncRoleMapping {

  // Note that custom roles defined at the project level cannot contain the
  // "resourcemanager.projects.list" permission, even though it was previously included here.
  // See https://cloud.google.com/iam/docs/understanding-custom-roles#known_limitations
  private static final List<String> PROJECT_READER_PERMISSIONS =
      ImmutableList.of(
          // View and get artifacts, view repository metadata. See
          // https://cloud.google.com/artifact-registry/docs/access-control.
          "artifactregistry.repositories.list",
          "artifactregistry.repositories.get",
          "artifactregistry.repositories.downloadArtifacts",
          "artifactregistry.files.list",
          "artifactregistry.files.get",
          "artifactregistry.repositories.listEffectiveTags",
          "artifactregistry.packages.list",
          "artifactregistry.tags.list",
          "artifactregistry.tags.get",
          "artifactregistry.versions.list",
          "artifactregistry.versions.get",
          "artifactregistry.locations.list",
          "artifactregistry.locations.get",
          "bigquery.jobs.create",
          "bigquery.readsessions.create",
          "bigquery.readsessions.getData",
          "bigquery.readsessions.update",
          "cloudbuild.builds.get",
          "cloudbuild.builds.list",
          "compute.acceleratorTypes.list",
          "compute.diskTypes.list",
          "compute.instances.get",
          "compute.instances.list",
          "compute.machineTypes.list",
          "compute.subnetworks.list",
          "lifesciences.operations.get",
          "lifesciences.operations.list",
          "monitoring.timeSeries.list",
          "notebooks.instances.list",
          "notebooks.instances.get",
          "notebooks.instances.checkUpgradability",
          "resourcemanager.projects.get",
          "serviceusage.operations.get",
          "serviceusage.operations.list",
          "serviceusage.quotas.get",
          "serviceusage.services.get",
          "serviceusage.services.list",
          "storage.buckets.list",
          "dataproc.clusters.get",
          "dataproc.clusters.list",
          "dataproc.jobs.get",
          "dataproc.jobs.list"
          );
  private static final List<String> PROJECT_WRITER_PERMISSIONS =
      new ImmutableList.Builder<String>()
          .addAll(PROJECT_READER_PERMISSIONS)
          .add(
              // read and write artifacts. See
              // https://cloud.google.com/artifact-registry/docs/access-control
              "artifactregistry.repositories.uploadArtifacts",
              "artifactregistry.tags.create",
              "artifactregistry.tags.update",
              "cloudbuild.builds.create",
              "cloudbuild.builds.update",
              "compute.instances.getGuestAttributes",
              "iam.serviceAccounts.get",
              "iam.serviceAccounts.list",
              "lifesciences.operations.cancel",
              "lifesciences.workflows.run",
              "notebooks.operations.cancel",
              "notebooks.operations.delete",
              "notebooks.operations.get",
              "notebooks.operations.list",
              "serviceusage.services.use")
          .build();

  private static final List<String> PROJECT_OWNER_PERMISSIONS =
      new ImmutableList.Builder<String>()
          .addAll(PROJECT_WRITER_PERMISSIONS)
          .add(
              // Create and manage repositories and artifacts.
              // See https://cloud.google.com/artifact-registry/docs/access-control.
              "artifactregistry.repositories.deleteArtifacts",
              "artifactregistry.packages.delete",
              "artifactregistry.projectsettings.update",
              "artifactregistry.tags.delete",
              "artifactregistry.versions.delete",
              "artifactregistry.repositories.create",
              "artifactregistry.repositories.createTagBinding",
              "artifactregistry.repositories.delete",
              "artifactregistry.repositories.deleteTagBinding",
              "artifactregistry.repositories.getIamPolicy",
              "artifactregistry.repositories.setIamPolicy",
              "artifactregistry.repositories.update",
              "cloudbuild.builds.approve",
              // Dataproc CRUD permissions
              // TODO: Move read permissions to PROJECT_READER and revoke modify permissions once adding wsm managed dataproc
              "dataproc.clusters.create",
              "dataproc.clusters.update",
              "dataproc.clusters.delete",
              "dataproc.clusters.use",
              "dataproc.clusters.start",
              "dataproc.clusters.stop",
              "dataproc.jobs.create",
              "dataproc.jobs.update",
              "dataproc.jobs.delete",
              "dataproc.jobs.cancel",
              "dataproc.autoscalingPolicies.get",
              "dataproc.autoscalingPolicies.list",
              "dataproc.autoscalingPolicies.create",
              "dataproc.autoscalingPolicies.update",
              "dataproc.autoscalingPolicies.delete",
              // TODO: Remove all of the following once adding WSM managed dataproc
              "dataproc.tasks.lease",
              "dataproc.tasks.listInvalidatedLeases",
              "dataproc.tasks.reportStatus",
              "dataproc.agents.get",
              "dataproc.agents.create",
              "dataproc.agents.update",
              "dataproc.agents.delete",
              "logging.logEntries.create",
              "monitoring.metricDescriptors.create",
              "monitoring.metricDescriptors.get",
              "monitoring.metricDescriptors.list",
              "monitoring.monitoredResourceDescriptors.get",
              "monitoring.monitoredResourceDescriptors.list",
              "monitoring.timeSeries.create",
              "compute.machineTypes.get",
              "compute.networks.get",
              "compute.networks.list",
              "compute.projects.get",
              "compute.regions.get",
              "compute.regions.list",
              "compute.zones.get",
              "compute.zones.list")
          .build();

  private static final CustomGcpIamRole PROJECT_READER =
      CustomGcpIamRole.of("PROJECT_READER", PROJECT_READER_PERMISSIONS);
  private static final CustomGcpIamRole PROJECT_WRITER =
      CustomGcpIamRole.of("PROJECT_WRITER", PROJECT_WRITER_PERMISSIONS);
  private static final CustomGcpIamRole PROJECT_OWNER =
      CustomGcpIamRole.of("PROJECT_OWNER", PROJECT_OWNER_PERMISSIONS);
  // Currently, workspace editors, applications and owners have the same cloud permissions as
  // writers. If that changes, create a new CustomGcpIamRole and modify the map below.
  public static final ImmutableMap<WsmIamRole, CustomGcpIamRole> CUSTOM_GCP_PROJECT_IAM_ROLES =
      ImmutableMap.of(
          WsmIamRole.OWNER, PROJECT_OWNER,
          // TODO: this should map to PROJECT_APPLICATION if that's created.
          WsmIamRole.APPLICATION, PROJECT_WRITER,
          WsmIamRole.WRITER, PROJECT_WRITER,
          WsmIamRole.READER, PROJECT_READER);

  public static final ImmutableSet<CustomGcpIamRole> CUSTOM_GCP_IAM_ROLES =
      // convert it to a set to get rid of the duplication.
      ImmutableSet.copyOf(CUSTOM_GCP_PROJECT_IAM_ROLES.values());
}
