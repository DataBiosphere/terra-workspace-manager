package bio.terra.workspace.service.resource.controlled.mappings;

import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.WsmResourceType;
import com.google.common.collect.ImmutableList;
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
 */
public class CustomGcpIamRoleMapping {
  static final ImmutableList<String> GCS_BUCKET_READER_PERMISSIONS =
      ImmutableList.of("storage.objects.list", "storage.objects.get");
  static final ImmutableList<String> GCS_BUCKET_WRITER_PERMISSIONS =
      new ImmutableList.Builder<String>()
          .addAll(GCS_BUCKET_READER_PERMISSIONS)
          .add("storage.objects.create", "storage.objects.delete", "storage.objects.update")
          .build();
  static final ImmutableList<String> GCS_BUCKET_EDITOR_PERMISSIONS =
      new ImmutableList.Builder<String>()
          .addAll(GCS_BUCKET_WRITER_PERMISSIONS)
          .add("storage.buckets.get")
          .build();
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
      new ImmutableList.Builder<String>()
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
          .build();
  static final ImmutableList<String> BIG_QUERY_DATASET_EDITOR_PERMISSIONS =
      new ImmutableList.Builder<String>()
          .addAll(BIG_QUERY_DATASET_WRITER_PERMISSIONS)
          .add("bigquery.datasets.getIamPolicy")
          .add("bigquery.tables.create")
          .add("bigquery.tables.delete")
          .add("bigquery.tables.update")
          .build();
  static final ImmutableList<String> AI_NOTEBOOK_INSTANCE_READER_PERMISSIONS =
      ImmutableList.of(
          "notebooks.instances.get",
          "notebooks.instances.list",
          "notebooks.locations.get",
          "notebooks.locations.list");
  // The 'iam.serviceAccounts.actAs' permission on the service account running the instance VM is
  // used to control write access to the notebook instance.
  static final ImmutableList<String> AI_NOTEBOOK_INSTANCE_WRITER_PERMISSIONS =
      new ImmutableList.Builder<String>()
          .addAll(AI_NOTEBOOK_INSTANCE_READER_PERMISSIONS)
          .add(
              "notebooks.instances.reset",
              "notebooks.instances.setAccelerator",
              "notebooks.instances.setMachineType",
              "notebooks.instances.start",
              "notebooks.instances.stop",
              "notebooks.instances.use")
          .build();
  static final ImmutableList<String> AI_NOTEBOOK_INSTANCE_EDITOR_PERMISSIONS =
      new ImmutableList.Builder<String>()
          .addAll(AI_NOTEBOOK_INSTANCE_WRITER_PERMISSIONS)
          .add(
              "notebooks.instances.getIamPolicy",
              "notebooks.operations.cancel",
              "notebooks.operations.delete",
              "notebooks.operations.get",
              "notebooks.operations.list")
          .build();
  public static final Table<WsmResourceType, ControlledResourceIamRole, CustomGcpIamRole>
      CUSTOM_GCP_RESOURCE_IAM_ROLES =
          new ImmutableTable.Builder<WsmResourceType, ControlledResourceIamRole, CustomGcpIamRole>()
              // GCS bucket
              .put(
                  WsmResourceType.GCS_BUCKET,
                  ControlledResourceIamRole.READER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceType.GCS_BUCKET,
                      ControlledResourceIamRole.READER,
                      GCS_BUCKET_READER_PERMISSIONS))
              .put(
                  WsmResourceType.GCS_BUCKET,
                  ControlledResourceIamRole.WRITER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceType.GCS_BUCKET,
                      ControlledResourceIamRole.WRITER,
                      GCS_BUCKET_WRITER_PERMISSIONS))
              .put(
                  WsmResourceType.GCS_BUCKET,
                  ControlledResourceIamRole.EDITOR,
                  CustomGcpIamRole.ofResource(
                      WsmResourceType.GCS_BUCKET,
                      ControlledResourceIamRole.EDITOR,
                      GCS_BUCKET_EDITOR_PERMISSIONS))
              // BigQuery dataset
              .put(
                  WsmResourceType.BIG_QUERY_DATASET,
                  ControlledResourceIamRole.READER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceType.BIG_QUERY_DATASET,
                      ControlledResourceIamRole.READER,
                      BIG_QUERY_DATASET_READER_PERMISSIONS))
              .put(
                  WsmResourceType.BIG_QUERY_DATASET,
                  ControlledResourceIamRole.WRITER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceType.BIG_QUERY_DATASET,
                      ControlledResourceIamRole.WRITER,
                      BIG_QUERY_DATASET_WRITER_PERMISSIONS))
              .put(
                  WsmResourceType.BIG_QUERY_DATASET,
                  ControlledResourceIamRole.EDITOR,
                  CustomGcpIamRole.ofResource(
                      WsmResourceType.BIG_QUERY_DATASET,
                      ControlledResourceIamRole.EDITOR,
                      BIG_QUERY_DATASET_EDITOR_PERMISSIONS))
              // AI Notebook instance
              .put(
                  WsmResourceType.AI_NOTEBOOK_INSTANCE,
                  ControlledResourceIamRole.READER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceType.AI_NOTEBOOK_INSTANCE,
                      ControlledResourceIamRole.READER,
                      AI_NOTEBOOK_INSTANCE_READER_PERMISSIONS))
              .put(
                  WsmResourceType.AI_NOTEBOOK_INSTANCE,
                  ControlledResourceIamRole.WRITER,
                  CustomGcpIamRole.ofResource(
                      WsmResourceType.AI_NOTEBOOK_INSTANCE,
                      ControlledResourceIamRole.WRITER,
                      AI_NOTEBOOK_INSTANCE_WRITER_PERMISSIONS))
              .put(
                  WsmResourceType.AI_NOTEBOOK_INSTANCE,
                  ControlledResourceIamRole.EDITOR,
                  CustomGcpIamRole.ofResource(
                      WsmResourceType.AI_NOTEBOOK_INSTANCE,
                      ControlledResourceIamRole.EDITOR,
                      AI_NOTEBOOK_INSTANCE_EDITOR_PERMISSIONS))
              .build();

  private CustomGcpIamRoleMapping() {}
}
