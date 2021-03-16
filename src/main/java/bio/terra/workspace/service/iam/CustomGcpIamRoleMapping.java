package bio.terra.workspace.service.iam;

import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.WsmResourceType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * This class specifies a static list of all GCP custom IAM roles that will be created in Workspace
 * contexts. To modify a role's permission, edit the appropriate list here. Note that currently
 * OWNER roles also receive all WRITER permissions, and WRITER roles also receive all READER
 * permissions.
 *
 * <p>We expect this mapping to change over time. There is currently no migration infrastructure for
 * these roles in existing projects. Editing these lists will affect newly created workspace
 * contexts, but WSM will not retroactively apply changes to existing projects.
 *
 * <p>Although WSM also supports the EDITOR and APPLICATION workspace IAM roles, these roles only
 * grant additional permissions through Sam. On GCP, those roles have the same permissions as
 * WRITERs.
 */
public class CustomGcpIamRoleMapping {
  @VisibleForTesting
  public static final ImmutableList<String> GCS_BUCKET_READER_PERMISSIONS =
      ImmutableList.of("storage.objects.list", "storage.objects.get");

  @VisibleForTesting
  public static final ImmutableList<String> GCS_BUCKET_WRITER_PERMISSIONS =
      new ImmutableList.Builder<String>()
          .addAll(GCS_BUCKET_READER_PERMISSIONS)
          .add("storage.objects.create", "storage.objects.delete")
          .build();

  @VisibleForTesting
  public static final ImmutableList<String> GCS_BUCKET_OWNER_PERMISSIONS =
      new ImmutableList.Builder<String>()
          .addAll(GCS_BUCKET_WRITER_PERMISSIONS)
          .add("storage.buckets.get")
          .build();

  @VisibleForTesting
  public static final ImmutableList<String> BIG_QUERY_DATASET_READER_PERMISSIONS =
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

  @VisibleForTesting
  public static final ImmutableList<String> BIG_QUERY_DATASET_WRITER_PERMISSIONS =
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

  @VisibleForTesting
  public static final ImmutableList<String> BIG_QUERY_DATASET_OWNER_PERMISSIONS =
      new ImmutableList.Builder<String>()
          .addAll(BIG_QUERY_DATASET_WRITER_PERMISSIONS)
          .add(
              "bigquery.datasets.getIamPolicy",
              "bigquery.tables.create",
              "bigquery.tables.delete",
              "bigquery.tables.update")
          .build();

  public static final ImmutableSet<CustomGcpIamRole> CUSTOM_GCP_IAM_ROLES =
      ImmutableSet.of(
          new CustomGcpIamRole(
              WsmResourceType.GCS_BUCKET, WsmIamRole.READER, GCS_BUCKET_READER_PERMISSIONS),
          new CustomGcpIamRole(
              WsmResourceType.GCS_BUCKET, WsmIamRole.WRITER, GCS_BUCKET_WRITER_PERMISSIONS),
          new CustomGcpIamRole(
              WsmResourceType.GCS_BUCKET, WsmIamRole.OWNER, GCS_BUCKET_OWNER_PERMISSIONS),
          new CustomGcpIamRole(
              WsmResourceType.BIG_QUERY_DATASET,
              WsmIamRole.READER,
              BIG_QUERY_DATASET_READER_PERMISSIONS),
          new CustomGcpIamRole(
              WsmResourceType.BIG_QUERY_DATASET,
              WsmIamRole.WRITER,
              BIG_QUERY_DATASET_WRITER_PERMISSIONS),
          new CustomGcpIamRole(
              WsmResourceType.BIG_QUERY_DATASET,
              WsmIamRole.OWNER,
              BIG_QUERY_DATASET_OWNER_PERMISSIONS));
}
