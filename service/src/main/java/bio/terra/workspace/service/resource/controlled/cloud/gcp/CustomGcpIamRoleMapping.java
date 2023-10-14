package bio.terra.workspace.service.resource.controlled.cloud.gcp;

import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;

/**
 * This class specifies all of the GCP custom IAM roles that will be created in Workspace contexts.
 * To modify a role's permission, edit the appropriate list here. Most resource are hierarchical:
 * EDITOR contains WRITER contains READER. The DELETER role is an exception as it only provides the
 * delete action for private resources, with no other access.
 *
 * <p>We expect this mapping to change over time, and new entries should be added as we add new
 * controlled resource types. There is currently no migration infrastructure for these roles in
 * existing projects. Editing these lists will affect newly created workspace contexts, but WSM will
 * not retroactively apply changes to existing projects.
 *
 * <p>!!!If you change this file, if you want to backfill the change to existing projects, contact
 * admin to run syncIamRoles endpoint.!!!
 */
public class CustomGcpIamRoleMapping {
  static final ImmutableList<String> GCS_BUCKET_READER_PERMISSIONS =
      ImmutableList.of("storage.buckets.get", "storage.objects.list", "storage.objects.get");
  static final ImmutableList<String> GCS_BUCKET_WRITER_PERMISSIONS =
      new ImmutableSet.Builder<String>()
          .addAll(GCS_BUCKET_READER_PERMISSIONS)
          .add("storage.objects.create", "storage.objects.delete", "storage.objects.update")
          .build()
          .asList();
  static final ImmutableList<String> GCS_BUCKET_EDITOR_PERMISSIONS =
      new ImmutableSet.Builder<String>().addAll(GCS_BUCKET_WRITER_PERMISSIONS).build().asList();
  static final ImmutableList<String> BIG_QUERY_DATASET_READER_PERMISSIONS =
      ImmutableList.of(
          "bigquery.datasets.get",
          "bigquery.models.export",
          "bigquery.models.getData",
          "bigquery.models.getMetadata",
          "bigquery.models.list",
          "bigquery.routines.get",
          "bigquery.routines.list",
          "bigquery.tables.export",
          "bigquery.tables.getData",
          "bigquery.tables.list",
          "bigquery.tables.get");
  static final ImmutableList<String> BIG_QUERY_DATASET_WRITER_PERMISSIONS =
      new ImmutableSet.Builder<String>()
          .addAll(BIG_QUERY_DATASET_READER_PERMISSIONS)
          .add(
              "bigquery.models.create",
              "bigquery.models.delete",
              "bigquery.models.updateData",
              "bigquery.models.updateMetadata",
              "bigquery.routines.create",
              "bigquery.routines.delete",
              "bigquery.routines.update",
              "bigquery.tables.updateData")
          .build()
          .asList();
  static final ImmutableList<String> BIG_QUERY_DATASET_EDITOR_PERMISSIONS =
      new ImmutableSet.Builder<String>()
          .addAll(BIG_QUERY_DATASET_WRITER_PERMISSIONS)
          .add("bigquery.datasets.getIamPolicy")
          .add("bigquery.tables.create")
          .add("bigquery.tables.delete")
          .add("bigquery.tables.update")
          .build()
          .asList();
  // see https://cloud.google.com/iap/docs/using-tcp-forwarding#grant-access-vm
  static final ImmutableList<String> IAP_TCP_FORWARDING_PERMISSIONS =
      ImmutableList.of(
          "iap.tunnelInstances.accessViaIAP",
          "compute.instances.get",
          "compute.instances.list",
          "compute.projects.get",
          "compute.instances.setMetadata",
          "compute.projects.setCommonInstanceMetadata",
          "compute.globalOperations.get");
  static final ImmutableList<String> AI_NOTEBOOK_INSTANCE_READER_PERMISSIONS =
      ImmutableList.of(
          "notebooks.instances.get",
          "notebooks.instances.list",
          "notebooks.instances.checkUpgradability",
          "notebooks.locations.get",
          "notebooks.locations.list");
  // The 'iam.serviceAccounts.actAs' permission on the service account running the instance VM is
  // used to control write access to the notebook instance.
  static final ImmutableList<String> AI_NOTEBOOK_INSTANCE_WRITER_PERMISSIONS =
      new ImmutableSet.Builder<String>()
          .addAll(AI_NOTEBOOK_INSTANCE_READER_PERMISSIONS)
          .add(
              "compute.instances.setMetadata",
              "notebooks.instances.reset",
              "notebooks.instances.setAccelerator",
              "notebooks.instances.setMachineType",
              "notebooks.instances.start",
              "notebooks.instances.stop",
              "notebooks.instances.use")
          .build()
          .asList();
  static final ImmutableList<String> AI_NOTEBOOK_INSTANCE_EDITOR_PERMISSIONS =
      new ImmutableSet.Builder<String>()
          .addAll(AI_NOTEBOOK_INSTANCE_WRITER_PERMISSIONS)
          .add(
              "notebooks.instances.getIamPolicy",
              "notebooks.operations.cancel",
              "notebooks.operations.delete",
              "notebooks.operations.get",
              "notebooks.operations.list")
          .build()
          .asList();
  static final ImmutableList<String> GCE_INSTANCE_READER_PERMISSIONS =
      ImmutableList.of("compute.instances.get", "compute.instances.list");

  static final ImmutableList<String> GCE_INSTANCE_WRITER_PERMISSIONS =
      new ImmutableSet.Builder<String>()
          .addAll(GCE_INSTANCE_READER_PERMISSIONS)
          .addAll(IAP_TCP_FORWARDING_PERMISSIONS)
          .add(
              "compute.instances.start",
              "compute.instances.stop",
              "compute.instances.use",
              "compute.instances.reset",
              "compute.instances.setMachineType")
          .build()
          .asList();
  static final ImmutableList<String> GCE_INSTANCE_EDITOR_PERMISSIONS =
      new ImmutableSet.Builder<String>()
          .addAll(GCE_INSTANCE_WRITER_PERMISSIONS)
          .add(
              "compute.instances.getIamPolicy",
              "compute.zoneOperations.get",
              "compute.zoneOperations.delete",
              "compute.zoneOperations.list")
          .build()
          .asList();
  static final ImmutableList<String> DATAPROC_CLUSTER_READER_PERMISSIONS =
      ImmutableList.of("compute.instances.get", "compute.instances.list", "dataproc.clusters.get");
  static final ImmutableList<String> DATAPROC_CLUSTER_WRITER_PERMISSIONS =
      new ImmutableSet.Builder<String>()
          .addAll(DATAPROC_CLUSTER_READER_PERMISSIONS)
          .add(
              "dataproc.clusters.use",
              "dataproc.clusters.start",
              "dataproc.clusters.stop",
              "dataproc.clusters.update")
          .build()
          .asList();
  static final ImmutableList<String> DATAPROC_CLUSTER_EDITOR_PERMISSIONS =
      new ImmutableSet.Builder<String>()
          .addAll(DATAPROC_CLUSTER_WRITER_PERMISSIONS)
          .add("dataproc.clusters.getIamPolicy")
          .build()
          .asList();

  public static final Table<WsmResourceType, ControlledResourceIamRole, CustomGcpIamRole>
      CUSTOM_GCP_RESOURCE_IAM_ROLES =
          new ImmutableTable.Builder<WsmResourceType, ControlledResourceIamRole, CustomGcpIamRole>()
              // GCS bucket
              .put(
                  WsmResourceType.CONTROLLED_GCP_GCS_BUCKET,
                  ControlledResourceIamRole.READER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceFamily.GCS_BUCKET,
                      ControlledResourceIamRole.READER,
                      GCS_BUCKET_READER_PERMISSIONS))
              .put(
                  WsmResourceType.CONTROLLED_GCP_GCS_BUCKET,
                  ControlledResourceIamRole.WRITER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceFamily.GCS_BUCKET,
                      ControlledResourceIamRole.WRITER,
                      GCS_BUCKET_WRITER_PERMISSIONS))
              .put(
                  WsmResourceType.CONTROLLED_GCP_GCS_BUCKET,
                  ControlledResourceIamRole.EDITOR,
                  CustomGcpIamRole.ofResource(
                      WsmResourceFamily.GCS_BUCKET,
                      ControlledResourceIamRole.EDITOR,
                      GCS_BUCKET_EDITOR_PERMISSIONS))
              // BigQuery dataset
              .put(
                  WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET,
                  ControlledResourceIamRole.READER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceFamily.BIG_QUERY_DATASET,
                      ControlledResourceIamRole.READER,
                      BIG_QUERY_DATASET_READER_PERMISSIONS))
              .put(
                  WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET,
                  ControlledResourceIamRole.WRITER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceFamily.BIG_QUERY_DATASET,
                      ControlledResourceIamRole.WRITER,
                      BIG_QUERY_DATASET_WRITER_PERMISSIONS))
              .put(
                  WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET,
                  ControlledResourceIamRole.EDITOR,
                  CustomGcpIamRole.ofResource(
                      WsmResourceFamily.BIG_QUERY_DATASET,
                      ControlledResourceIamRole.EDITOR,
                      BIG_QUERY_DATASET_EDITOR_PERMISSIONS))
              // AI Notebook instance
              .put(
                  WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE,
                  ControlledResourceIamRole.READER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceFamily.AI_NOTEBOOK_INSTANCE,
                      ControlledResourceIamRole.READER,
                      AI_NOTEBOOK_INSTANCE_READER_PERMISSIONS))
              .put(
                  WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE,
                  ControlledResourceIamRole.WRITER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceFamily.AI_NOTEBOOK_INSTANCE,
                      ControlledResourceIamRole.WRITER,
                      AI_NOTEBOOK_INSTANCE_WRITER_PERMISSIONS))
              .put(
                  WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE,
                  ControlledResourceIamRole.EDITOR,
                  CustomGcpIamRole.ofResource(
                      WsmResourceFamily.AI_NOTEBOOK_INSTANCE,
                      ControlledResourceIamRole.EDITOR,
                      AI_NOTEBOOK_INSTANCE_EDITOR_PERMISSIONS))
              // GCE instance
              .put(
                  WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE,
                  ControlledResourceIamRole.READER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceFamily.GCE_INSTANCE,
                      ControlledResourceIamRole.READER,
                      GCE_INSTANCE_READER_PERMISSIONS))
              .put(
                  WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE,
                  ControlledResourceIamRole.WRITER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceFamily.GCE_INSTANCE,
                      ControlledResourceIamRole.WRITER,
                      GCE_INSTANCE_WRITER_PERMISSIONS))
              .put(
                  WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE,
                  ControlledResourceIamRole.EDITOR,
                  CustomGcpIamRole.ofResource(
                      WsmResourceFamily.GCE_INSTANCE,
                      ControlledResourceIamRole.EDITOR,
                      GCE_INSTANCE_EDITOR_PERMISSIONS))
              // Dataproc cluster
              .put(
                  WsmResourceType.CONTROLLED_GCP_DATAPROC_CLUSTER,
                  ControlledResourceIamRole.READER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceFamily.DATAPROC_CLUSTER,
                      ControlledResourceIamRole.READER,
                      DATAPROC_CLUSTER_READER_PERMISSIONS))
              .put(
                  WsmResourceType.CONTROLLED_GCP_DATAPROC_CLUSTER,
                  ControlledResourceIamRole.WRITER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceFamily.DATAPROC_CLUSTER,
                      ControlledResourceIamRole.WRITER,
                      DATAPROC_CLUSTER_WRITER_PERMISSIONS))
              .put(
                  WsmResourceType.CONTROLLED_GCP_DATAPROC_CLUSTER,
                  ControlledResourceIamRole.EDITOR,
                  CustomGcpIamRole.ofResource(
                      WsmResourceFamily.DATAPROC_CLUSTER,
                      ControlledResourceIamRole.EDITOR,
                      DATAPROC_CLUSTER_EDITOR_PERMISSIONS))
              .build();

  private CustomGcpIamRoleMapping() {}
}
