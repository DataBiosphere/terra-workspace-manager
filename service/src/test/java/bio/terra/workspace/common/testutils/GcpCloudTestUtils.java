package bio.terra.workspace.common.testutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.cloudres.google.bigquery.BigQueryCow;
import bio.terra.workspace.common.utils.RetryUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import com.google.api.gax.paging.Page;
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
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Utils for working with cloud objects. */
@Component
public class GcpCloudTestUtils {
  private static final Logger logger = LoggerFactory.getLogger(GcpCloudTestUtils.class);

  @Autowired CrlService crlService;

  public static final String BQ_EMPLOYEE_TABLE_NAME = "employee";
  public static final int BQ_EMPLOYEE_ID = 100;

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
    Schema employeeSchema = Schema.of(Field.of("employee_id", LegacySQLTypeName.INTEGER));
    TableId employeeTableId = TableId.of(projectId, datasetId, BQ_EMPLOYEE_TABLE_NAME);
    TableInfo employeeTableInfo =
        TableInfo.newBuilder(employeeTableId, StandardTableDefinition.of(employeeSchema)).build();
    Table createdEmployeeTable =
        RetryUtils.getWithRetryOnException(() -> bigQueryClient.create(employeeTableInfo));
    logger.debug("Employee Table: {}", createdEmployeeTable);

    // Don't call insertAll() with InsertAllRequest. That inserts via stream. Stream buffer may
    // not be copied for up to 90 minutes:
    // https://cloud.google.com/bigquery/docs/streaming-data-into-bigquery#dataavailability
    // Instead, use DDL to insert rows.
    RetryUtils.getWithRetryOnException(
        () ->
            bigQueryClient.query(
                QueryJobConfiguration.newBuilder(
                        "INSERT INTO `%s.%s.%s` (employee_id) VALUES(%s)"
                            .formatted(
                                projectId, datasetId, BQ_EMPLOYEE_TABLE_NAME, BQ_EMPLOYEE_ID))
                    .build()),
        RetryUtils.DEFAULT_RETRY_TOTAL_DURATION,
        Duration.ofSeconds(5),
        0.5,
        RetryUtils.DEFAULT_RETRY_SLEEP_DURATION_MAX,
        Collections.singletonList(BigQueryException.class));
  }

  /** Asserts table is populated as per populateBqTable(). */
  public void assertBqTableContents(
      GoogleCredentials userCredential, String projectId, String datasetId) throws Exception {
    BigQuery bigQueryClient = getGcpBigQueryClient(userCredential, projectId);
    Page<FieldValueList> actualRows =
        RetryUtils.getWithRetryOnException(
            () -> bigQueryClient.listTableData(TableId.of(datasetId, BQ_EMPLOYEE_TABLE_NAME)));

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
        RetryUtils.getWithRetryOnException(
            () -> bigQueryCow.tables().list(projectId, datasetId).execute().getTables());
    assertNull(actualTables);
  }

  private static BigQuery getGcpBigQueryClient(GoogleCredentials userCredential, String projectId) {
    return BigQueryOptions.newBuilder()
        .setCredentials(userCredential)
        .setProjectId(projectId)
        .build()
        .getService();
  }

  /**
   * Get a result from a call that might throw an exception. Treat the exception as retryable, sleep
   * for 15 seconds, and retry up to 80 times. This structure is useful for situations where we are
   * waiting on a cloud IAM permission change to take effect.
   *
   * @param supplier - code returning the result or throwing an exception
   * @param <T> - type of result
   * @return - result from supplier, the first time it doesn't throw, or null if all tries have been
   *     exhausted
   * @throws InterruptedException InterruptedException
   */
  public static @Nullable <T> T getWithRetryOnException(SupplierWithException<T> supplier)
      throws Exception {
    T result = null;
    int numTries = 80;
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
