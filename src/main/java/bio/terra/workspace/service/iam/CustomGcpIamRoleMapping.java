package bio.terra.workspace.service.iam;

import bio.terra.workspace.service.iam.model.IamRole;
import bio.terra.workspace.service.resource.controlled.WsmResourceType;
import com.google.common.collect.ImmutableList;

/**
 * This class specifies a static list of all GCP custom IAM roles that will be created in Workspace
 * contexts. To modify a role's permission, edit the appropriate list here. Note that currently
 * OWNER roles also receive all WRITER permissions, and WRITER roles also receive all READER
 * permissions.
 *
 * <p>We expect this mapping to change over time. There is currently no migration infrastructure for
 * these roles in existing projects. Editing these lists will affect newly created workspace
 * contexts, but WSM will not retroactively apply changes to existing projects.
 */
public class CustomGcpIamRoleMapping {
  private static final ImmutableList<String> gcsBucketReaderPermissions =
      ImmutableList.of("storage.objects.list", "storage.objects.get");
  private static final ImmutableList<String> gcsBucketWriterPermissions =
      new ImmutableList.Builder<String>()
          .addAll(gcsBucketReaderPermissions)
          .add("storage.objects.create", "storage.objects.delete")
          .build();
  private static final ImmutableList<String> gcsBucketOwnerPermissions =
      new ImmutableList.Builder<String>()
          .addAll(gcsBucketWriterPermissions)
          .add("storage.buckets.get")
          .build();

  private static final ImmutableList<String> bigqueryDatasetReaderPermissions =
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
  private static final ImmutableList<String> bigqueryDatasetWriterPermissions =
      new ImmutableList.Builder<String>()
          .addAll(bigqueryDatasetReaderPermissions)
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
  private static final ImmutableList<String> bigqueryDatasetOwnerPermissions =
      new ImmutableList.Builder<String>()
          .addAll(bigqueryDatasetWriterPermissions)
          .add(
              "bigquery.datasets.getIamPolicy",
              "bigquery.tables.create",
              "bigquery.tables.delete",
              "bigquery.tables.update")
          .build();

  public static final ImmutableList<CustomGcpIamRole> customIamRoles =
      ImmutableList.of(
          new CustomGcpIamRole(
              WsmResourceType.GCS_BUCKET, IamRole.READER, gcsBucketReaderPermissions),
          new CustomGcpIamRole(
              WsmResourceType.GCS_BUCKET, IamRole.WRITER, gcsBucketWriterPermissions),
          new CustomGcpIamRole(
              WsmResourceType.GCS_BUCKET, IamRole.OWNER, gcsBucketOwnerPermissions),
          new CustomGcpIamRole(
              WsmResourceType.BIGQUERY_DATASET, IamRole.READER, bigqueryDatasetReaderPermissions),
          new CustomGcpIamRole(
              WsmResourceType.BIGQUERY_DATASET, IamRole.WRITER, bigqueryDatasetWriterPermissions),
          new CustomGcpIamRole(
              WsmResourceType.BIGQUERY_DATASET, IamRole.OWNER, bigqueryDatasetOwnerPermissions));
}
