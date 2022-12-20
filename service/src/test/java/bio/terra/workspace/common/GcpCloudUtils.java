package bio.terra.workspace.common;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.text.IsEqualIgnoringCase.equalToIgnoringCase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.cloudres.google.storage.BlobCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.gax.paging.Page;
import com.google.api.services.compute.Compute;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableInfo;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Utils for working with cloud objects. */
@Component
public class GcpCloudUtils {
  private static final Logger logger = LoggerFactory.getLogger(GcpCloudUtils.class);

  @Autowired private static CrlService crlService;

  public static final String BQ_EMPLOYEE_TABLE_NAME = "employee";
  public static final int BQ_EMPLOYEE_ID = 100;

  private static final String GCS_FILE_NAME = "foo";
  private static final String GCS_FILE_CONTENTS = "bar";

  @FunctionalInterface
  public interface SupplierWithException<T> {
    T get() throws Exception;
  }

  @FunctionalInterface
  public interface RunnableWithException {
    void run() throws Exception;
  }

  /** Creates a BQ table with one column: employee_id. There is one row with employee_id = 100. */
  public void populateBqTable(GoogleCredentials userCredential, String projectId, String datasetId)
      throws Exception {
    // Create employee table
    BigQuery bigQueryClient = getGcpBigQueryClient(userCredential, projectId);
    final Schema employeeSchema = Schema.of(Field.of("employee_id", LegacySQLTypeName.INTEGER));
    final TableId employeeTableId = TableId.of(projectId, datasetId, BQ_EMPLOYEE_TABLE_NAME);
    final TableInfo employeeTableInfo =
        TableInfo.newBuilder(employeeTableId, StandardTableDefinition.of(employeeSchema)).build();
    final Table createdEmployeeTable = bigQueryClient.create(employeeTableInfo);
    logger.debug("Employee Table: {}", createdEmployeeTable);

    // Add row to table
    // Retry because if project was created recently, it may take time for bigquery.jobs.create to
    // propagate
    int retryCount = 10;
    int retryWaitSeconds = 5;
    for (int i = 0; i < retryCount; i++) {
      TimeUnit.SECONDS.sleep(retryWaitSeconds);
      try {
        // Don't call insertAll() with InsertAllRequest. That inserts via stream. Stream buffer may
        // not be copied for up to 90 minutes:
        // https://cloud.google.com/bigquery/docs/streaming-data-into-bigquery#dataavailability
        // Instead, use DDL to insert rows.
        bigQueryClient.query(
            QueryJobConfiguration.newBuilder(
                    "INSERT INTO `%s.%s.%s` (employee_id) VALUES(%s)"
                        .formatted(projectId, datasetId, BQ_EMPLOYEE_TABLE_NAME, BQ_EMPLOYEE_ID))
                .build());
      } catch (BigQueryException e) {
        // bigquery.jobs.create hasn't propagated yet; retry
        if (e.getCode() == HttpStatus.FORBIDDEN.value()) {
          continue;
        }
        throw e;
      }
      // Insert succeeded
      break;
    }
  }

  /** Asserts table is populated as per populateBqTable(). */
  public void assertBqTableContents(
      GoogleCredentials userCredential, String projectId, String datasetId) {
    BigQuery bigQueryClient = getGcpBigQueryClient(userCredential, projectId);
    Page<FieldValueList> actualRows =
        bigQueryClient.listTableData(TableId.of(datasetId, BQ_EMPLOYEE_TABLE_NAME));

    int rowCount = 0;
    for (FieldValueList row : actualRows.getValues()) {
      rowCount++;
      assertEquals(BQ_EMPLOYEE_ID, row.get(0).getLongValue());
    }
    assertEquals(1, rowCount);
  }

  public void assertDatasetHasNoTables(
      AuthenticatedUserRequest userRequest, String projectId, String datasetId) throws Exception {
    BigQueryCow bigQueryCow = crlService.createBigQueryCow(userRequest);
    List<com.google.api.services.bigquery.model.TableList.Tables> actualTables =
        bigQueryCow.tables().list(projectId, datasetId).execute().getTables();
    assertNull(actualTables);
  }

  /** Adds a file called "foo" with the contents "bar". */
  public void addFileToBucket(
      GoogleCredentials userCredential, String projectId, String bucketName) {
    Storage storageClient = getGcpStorageClient(userCredential, projectId);
    BlobId blobId = BlobId.of(bucketName, GCS_FILE_NAME);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
    storageClient.create(blobInfo, GCS_FILE_CONTENTS.getBytes(StandardCharsets.UTF_8));
  }

  /** Asserts bucket has file as per addFileToBucket(). */
  public void assertBucketFiles(
      AuthenticatedUserRequest userRequest,
      GoogleCredentials userCredential,
      String projectId,
      String bucketName) {
    Storage storageClient = getGcpStorageClient(userCredential, projectId);
    String actualContents =
        new String(storageClient.readAllBytes(bucketName, GCS_FILE_NAME), StandardCharsets.UTF_8);
    assertEquals(GCS_FILE_CONTENTS, actualContents);
  }

  /** Asserts table is populated as per populateBqTable(). */
  public void assertBucketHasNoFiles(
      AuthenticatedUserRequest userRequest, String projectId, String bucketName) {
    StorageCow storageCow = crlService.createStorageCow(projectId, userRequest);
    int numFiles = 0;
    for (BlobCow blob : storageCow.get(bucketName).list().iterateAll()) {
      numFiles++;
    }
    assertEquals(0, numFiles);
  }

  public void assertBucketAttributes(
      AuthenticatedUserRequest userRequest,
      String projectId,
      String bucketName,
      String expectedLocation,
      ApiGcpGcsBucketDefaultStorageClass expectedStorageClass,
      List<LifecycleRule> expectedLifecycleRules) {
    StorageCow storageCow = crlService.createStorageCow(projectId, userRequest);
    BucketInfo actualBucketInfo = storageCow.get(bucketName).getBucketInfo();

    assertThat(expectedLocation, equalToIgnoringCase(actualBucketInfo.getLocation()));
    assertEquals(expectedStorageClass.name(), actualBucketInfo.getStorageClass().name());
    assertThat(
        actualBucketInfo.getLifecycleRules(), containsInAnyOrder(expectedLifecycleRules.toArray()));
  }

  private static BigQuery getGcpBigQueryClient(GoogleCredentials userCredential, String projectId) {
    return BigQueryOptions.newBuilder()
        .setCredentials(userCredential)
        .setProjectId(projectId)
        .build()
        .getService();
  }

  private static Storage getGcpStorageClient(GoogleCredentials userCredential, String projectId) {
    return StorageOptions.newBuilder()
        .setCredentials(userCredential)
        .setProjectId(projectId)
        .build()
        .getService();
  }

  /**
   * Directly calling the gcp api to get/update instance, requires the createComputeService, see the
   * example in https://cloud.google.com/compute/docs/reference/rest/v1/instances/get
   */
  public static Compute createComputeService() throws IOException, GeneralSecurityException {
    HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

    GoogleCredential credential = GoogleCredential.getApplicationDefault();
    if (credential.createScopedRequired()) {
      credential =
          credential.createScoped(Arrays.asList("https://www.googleapis.com/auth/cloud-platform"));
    }

    return new Compute.Builder(httpTransport, jsonFactory, credential)
        .setApplicationName("Google-ComputeSample/0.1")
        .build();
  }

  /**
   * Get a result from a call that might throw an exception. Treat the exception as retryable, sleep
   * for 15 seconds, and retry up to 40 times. This structure is useful for situations where we are
   * waiting on a cloud IAM permission change to take effect.
   *
   * @param supplier - code returning the result or throwing an exception
   * @param <T> - type of result
   * @return - result from supplier, the first time it doesn't throw, or null if all tries have been
   *     exhausted
   * @throws InterruptedException
   */
  public static @Nullable <T> T getWithRetryOnException(SupplierWithException<T> supplier)
      throws Exception {
    T result = null;
    int numTries = 40;
    Duration sleepDuration = Duration.ofSeconds(15);
    while (numTries > 0) {
      try {
        result = supplier.get();
        break;
      } catch (Exception e) {
        numTries--;
        if (0 == numTries) {
          throw e;
        }
        logger.info(
            "Exception \"{}\". Waiting {} seconds for permissions to propagate. Tries remaining: {}",
            e.getMessage(),
            sleepDuration.toSeconds(),
            numTries);
        TimeUnit.MILLISECONDS.sleep(sleepDuration.toMillis());
      }
    }
    return result;
  }

  public static void runWithRetryOnException(RunnableWithException fn) throws Exception {
    getWithRetryOnException(
        () -> {
          fn.run();
          return null;
        });
  }
}
