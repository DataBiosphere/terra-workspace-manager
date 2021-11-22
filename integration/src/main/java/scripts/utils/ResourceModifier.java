package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static scripts.utils.GcsBucketTestFixtures.GCS_BLOB_CONTENT;
import static scripts.utils.GcsBucketTestFixtures.GCS_BLOB_NAME;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.model.CreatedControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.TestIamPermissionsRequest;
import com.google.api.services.notebooks.v1.AIPlatformNotebooks;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableInfo;
import com.google.cloud.bigquery.TableResult;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static test utilities for modifying existing resources including reading, writing, and checking
 * access.
 */
public class ResourceModifier {

  private static final Logger logger = LoggerFactory.getLogger(ResourceModifier.class);

  public static Blob addFileToBucket(
      CreatedControlledGcpGcsBucket bucket, TestUserSpecification bucketWriter, String gcpProjectId)
      throws IOException, InterruptedException {
    final Storage sourceOwnerStorageClient =
        ClientTestUtils.getGcpStorageClient(bucketWriter, gcpProjectId);
    final BlobId blobId =
        BlobId.of(bucket.getGcpBucket().getAttributes().getBucketName(), GCS_BLOB_NAME);
    final BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
    return ClientTestUtils.getWithRetryOnException(
        () ->
            sourceOwnerStorageClient.create(
                blobInfo, GCS_BLOB_CONTENT.getBytes(StandardCharsets.UTF_8)));
  }

  public static Blob retrieveBucketFile(
      String bucketName, String gcpProjectId, TestUserSpecification bucketReader)
      throws IOException {
    Storage cloningUserStorageClient =
        ClientTestUtils.getGcpStorageClient(bucketReader, gcpProjectId);
    BlobId blobId = BlobId.of(bucketName, GCS_BLOB_NAME);

    final Blob retrievedFile = cloningUserStorageClient.get(blobId);
    assertNotNull(retrievedFile);
    assertEquals(blobId.getName(), retrievedFile.getBlobId().getName());
    return retrievedFile;
  }

  /**
   * Create two tables with multiple rows in them into the provided dataset. Uses a mixture of
   * streaming and DDL insertion to demonstrate the difference in copy job behavior.
   *
   * @param dataset - empty BigQuery dataset
   * @param ownerUser - User who owns the dataset
   * @param projectId - project that owns the dataset
   * @throws IOException
   * @throws InterruptedException
   */
  public static void populateBigQueryDataset(
      GcpBigQueryDatasetResource dataset, TestUserSpecification ownerUser, String projectId)
      throws IOException, InterruptedException {
    // Add tables to the source dataset
    final BigQuery bigQueryClient = ClientTestUtils.getGcpBigQueryClient(ownerUser, projectId);

    final Schema employeeSchema =
        Schema.of(
            Field.of("employee_id", LegacySQLTypeName.INTEGER),
            Field.of("name", LegacySQLTypeName.STRING));
    final TableId employeeTableId =
        TableId.of(projectId, dataset.getAttributes().getDatasetId(), "employee");

    final TableInfo employeeTableInfo =
        TableInfo.newBuilder(employeeTableId, StandardTableDefinition.of(employeeSchema))
            .setFriendlyName("Employee")
            .build();
    final Table createdEmployeeTable =
        ClientTestUtils.getWithRetryOnException(() -> bigQueryClient.create(employeeTableInfo));
    logger.debug("Employee Table: {}", createdEmployeeTable);

    final Table createdDepartmentTable =
        bigQueryClient.create(
            TableInfo.newBuilder(
                    TableId.of(projectId, dataset.getAttributes().getDatasetId(), "department"),
                    StandardTableDefinition.of(
                        Schema.of(
                            Field.of("department_id", LegacySQLTypeName.INTEGER),
                            Field.of("manager_id", LegacySQLTypeName.INTEGER),
                            Field.of("name", LegacySQLTypeName.STRING))))
                .setFriendlyName("Department")
                .build());
    logger.debug("Department Table: {}", createdDepartmentTable);

    // Add rows to the tables

    // Stream insert one row to check the error handling/warning. This row may not be copied. (If
    // the stream happens after the DDL insert, sometimes it gets copied).
    bigQueryClient.insertAll(
        InsertAllRequest.newBuilder(employeeTableInfo)
            .addRow(ImmutableMap.of("employee_id", 103, "name", "Batman"))
            .build());

    // Use DDL to insert rows instead of InsertAllRequest so that data won't
    // be in the streaming buffer where it's un-copyable for up to 90 minutes.
    bigQueryClient.query(
        QueryJobConfiguration.newBuilder(
                "INSERT INTO `"
                    + projectId
                    + "."
                    + dataset.getAttributes().getDatasetId()
                    + ".employee` (employee_id, name) VALUES("
                    + "101, 'Aquaman'), (102, 'Superman');")
            .build());

    bigQueryClient.query(
        QueryJobConfiguration.newBuilder(
                "INSERT INTO `"
                    + projectId
                    + "."
                    + dataset.getAttributes().getDatasetId()
                    + ".department` (department_id, manager_id, name) "
                    + "VALUES(201, 101, 'ocean'), (202, 102, 'sky');")
            .build());

    // double-check the rows are there
    final TableResult employeeTableResult =
        bigQueryClient.query(
            QueryJobConfiguration.newBuilder(
                    "SELECT * FROM `"
                        + projectId
                        + "."
                        + dataset.getAttributes().getDatasetId()
                        + ".employee`;")
                .build());
    final long numRows =
        StreamSupport.stream(employeeTableResult.getValues().spliterator(), false).count();
    assertThat(numRows, is(greaterThanOrEqualTo(2L)));
  }

  /**
   * Read and validate data populated by {@code populateBigQueryDataset} from a BigQuery dataset.
   * This is intended for validating that the provided user has read access to the given dataset.
   */
  public static TableResult readPopulatedBigQueryTable(
      GcpBigQueryDatasetResource dataset, TestUserSpecification user, String projectId)
      throws IOException, InterruptedException {

    final BigQuery bigQueryClient = ClientTestUtils.getGcpBigQueryClient(user, projectId);
    final TableResult employeeTableResult =
        bigQueryClient.query(
            QueryJobConfiguration.newBuilder(
                    "SELECT * FROM `"
                        + projectId
                        + "."
                        + dataset.getAttributes().getDatasetId()
                        + ".employee`;")
                .build());
    final long numRows =
        StreamSupport.stream(employeeTableResult.getValues().spliterator(), false).count();
    assertThat(numRows, is(greaterThanOrEqualTo(2L)));
    return employeeTableResult;
  }

  /**
   * Check whether the user has access to the Notebook through the proxy with a service account.
   *
   * <p>We can't directly test that we can go through the proxy to the Jupyter notebook without a
   * real Google user auth flow, so we check the necessary ingredients instead.
   */
  public static boolean userHasProxyAccess(
      CreatedControlledGcpAiNotebookInstanceResult createdNotebook,
      TestUserSpecification user,
      String projectId)
      throws GeneralSecurityException, IOException {

    String instanceName =
        String.format(
            "projects/%s/locations/%s/instances/%s",
            createdNotebook.getAiNotebookInstance().getAttributes().getProjectId(),
            createdNotebook.getAiNotebookInstance().getAttributes().getLocation(),
            createdNotebook.getAiNotebookInstance().getAttributes().getInstanceId());
    AIPlatformNotebooks userNotebooks = ClientTestUtils.getAIPlatformNotebooksClient(user);
    Instance instance;
    try {
      instance = userNotebooks.projects().locations().instances().get(instanceName).execute();
    } catch (GoogleJsonResponseException googleException) {
      // If we get a 403 or 404 when fetching the instance, the user does not have access.
      if (googleException.getStatusCode() == HttpStatus.SC_FORBIDDEN
          || googleException.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return false;
      } else {
        // If a different status code is thrown instead, rethrow here as that's an unexpected error.
        throw googleException;
      }
    }
    // Test that the user has access to the notebook with a service account through proxy mode.
    // git secrets gets a false positive if 'service_account' is double quoted.
    assertThat(
        "Notebook has correct proxy mode access",
        instance.getMetadata(),
        Matchers.hasEntry("proxy-mode", "service_" + "account"));

    // The user needs to have the actAs permission on the service account.
    String actAsPermission = "iam.serviceAccounts.actAs";
    String serviceAccountName =
        String.format("projects/%s/serviceAccounts/%s", projectId, instance.getServiceAccount());
    List<String> maybePermissionsList =
        ClientTestUtils.getGcpIamClient(user)
            .projects()
            .serviceAccounts()
            .testIamPermissions(
                serviceAccountName,
                new TestIamPermissionsRequest().setPermissions(List.of(actAsPermission)))
            .execute()
            .getPermissions();
    // GCP returns null rather than an empty list when a user does not have any permissions
    return Optional.ofNullable(maybePermissionsList)
        .map(list -> list.contains(actAsPermission))
        .orElse(false);
  }
}
