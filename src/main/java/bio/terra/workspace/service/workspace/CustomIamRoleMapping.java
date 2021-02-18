package bio.terra.workspace.service.workspace;

import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.iam.model.IamRole;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class CustomIamRoleMapping {
  // TODO: Top-level key should be controlled type, not reference type.
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
  private static final ImmutableMap<IamRole, ImmutableList<String>> gcsBucketRoleMap =
      ImmutableMap.of(
          IamRole.READER, gcsBucketReaderPermissions,
          IamRole.WRITER, gcsBucketWriterPermissions,
          IamRole.OWNER, gcsBucketOwnerPermissions);

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
  private static final ImmutableMap<IamRole, ImmutableList<String>> bigqueryDatasetRoleMap =
      ImmutableMap.of(
          IamRole.READER, bigqueryDatasetReaderPermissions,
          IamRole.WRITER, bigqueryDatasetWriterPermissions,
          IamRole.OWNER, bigqueryDatasetOwnerPermissions);

  public static final ImmutableMap<DataReferenceType, ImmutableMap<IamRole, ImmutableList<String>>>
      customIamRoleMap =
          ImmutableMap.of(
              DataReferenceType.GOOGLE_BUCKET, gcsBucketRoleMap,
              DataReferenceType.BIG_QUERY_DATASET, bigqueryDatasetRoleMap);
}
