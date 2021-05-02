package bio.terra.workspace.service.resource.controlled.mappings;

import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.WsmResourceType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import java.util.Collections;

/**
 * This class specifies all of the GCP custom IAM roles that will be created in Workspace contexts.
 * To modify a role's permission, edit the appropriate list here. Unlike workspace roles, resource
 * roles are not strictly hierarchical; the EDITOR role has a distinct set of permissions from
 * WRITER, it is not a superset.
 *
 * <p>We expect this mapping to change over time, and new entries should be added as we add new
 * controlled resource types. There is currently no migration infrastructure for these roles in
 * existing projects. Editing these lists will affect newly created workspace contexts, but WSM will
 * not retroactively apply changes to existing projects.
 */
public class CustomGcpIamRoleMapping {
  private CustomGcpIamRoleMapping() {}

  static final ImmutableList<String> GCS_BUCKET_READER_PERMISSIONS =
      ImmutableList.of("storage.objects.list", "storage.objects.get");

  static final ImmutableList<String> GCS_BUCKET_WRITER_PERMISSIONS =
      new ImmutableList.Builder<String>()
          .addAll(GCS_BUCKET_READER_PERMISSIONS)
          .add("storage.objects.create", "storage.objects.delete")
          .build();

  static final ImmutableList<String> GCS_BUCKET_EDITOR_PERMISSIONS =
      ImmutableList.of("storage.buckets.get");

  static final ImmutableList<String> BIG_QUERY_DATASET_READER_PERMISSIONS =
      ImmutableList.of(
          "bigquery.datasets.get",
          "bigquery.jobs.create",
          "bigquery.models.export",
          "bigquery.models.getData",
          "bigquery.models.getMetadata",
          "bigquery.models.list",
          "bigquery.routines.get",
          "bigquery.routines.list",
          "bigquery.tables.export",
          "bigquery.tables.getData",
          "bigquery.tables.list");

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
      ImmutableList.of(
          "bigquery.datasets.getIamPolicy",
          "bigquery.tables.create",
          "bigquery.tables.delete",
          "bigquery.tables.update");

  // The ASSIGNER role does not grant GCP permissions, only Sam permissions. It's included in
  // this map for completeness.
  public static final Table<WsmResourceType, ControlledResourceIamRole, CustomGcpIamRole>
      CUSTOM_GCP_IAM_ROLES =
          new ImmutableTable.Builder<WsmResourceType, ControlledResourceIamRole, CustomGcpIamRole>()
              // GCS bucket
              .put(
                  WsmResourceType.GCS_BUCKET,
                  ControlledResourceIamRole.READER,
                  new CustomGcpIamRole(
                      WsmResourceType.GCS_BUCKET,
                      ControlledResourceIamRole.READER,
                      GCS_BUCKET_READER_PERMISSIONS))
              .put(
                  WsmResourceType.GCS_BUCKET,
                  ControlledResourceIamRole.WRITER,
                  new CustomGcpIamRole(
                      WsmResourceType.GCS_BUCKET,
                      ControlledResourceIamRole.WRITER,
                      GCS_BUCKET_WRITER_PERMISSIONS))
              .put(
                  WsmResourceType.GCS_BUCKET,
                  ControlledResourceIamRole.EDITOR,
                  new CustomGcpIamRole(
                      WsmResourceType.GCS_BUCKET,
                      ControlledResourceIamRole.EDITOR,
                      GCS_BUCKET_EDITOR_PERMISSIONS))
              .put(
                  WsmResourceType.GCS_BUCKET,
                  ControlledResourceIamRole.ASSIGNER,
                  new CustomGcpIamRole(
                      WsmResourceType.GCS_BUCKET,
                      ControlledResourceIamRole.ASSIGNER,
                      Collections.emptyList()))
              // BigQuery dataset
              .put(
                  WsmResourceType.BIG_QUERY_DATASET,
                  ControlledResourceIamRole.READER,
                  new CustomGcpIamRole(
                      WsmResourceType.BIG_QUERY_DATASET,
                      ControlledResourceIamRole.READER,
                      BIG_QUERY_DATASET_READER_PERMISSIONS))
              .put(
                  WsmResourceType.BIG_QUERY_DATASET,
                  ControlledResourceIamRole.WRITER,
                  new CustomGcpIamRole(
                      WsmResourceType.BIG_QUERY_DATASET,
                      ControlledResourceIamRole.WRITER,
                      BIG_QUERY_DATASET_WRITER_PERMISSIONS))
              .put(
                  WsmResourceType.BIG_QUERY_DATASET,
                  ControlledResourceIamRole.EDITOR,
                  new CustomGcpIamRole(
                      WsmResourceType.BIG_QUERY_DATASET,
                      ControlledResourceIamRole.EDITOR,
                      BIG_QUERY_DATASET_EDITOR_PERMISSIONS))
              .put(
                  WsmResourceType.BIG_QUERY_DATASET,
                  ControlledResourceIamRole.ASSIGNER,
                  new CustomGcpIamRole(
                      WsmResourceType.BIG_QUERY_DATASET,
                      ControlledResourceIamRole.ASSIGNER,
                      Collections.emptyList()))
              .build();
}
