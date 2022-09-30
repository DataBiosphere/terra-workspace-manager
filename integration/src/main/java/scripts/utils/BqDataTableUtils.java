package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.GcpBigQueryDataTableAttributes;
import bio.terra.workspace.model.GcpBigQueryDataTableResource;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.UpdateBigQueryDataTableReferenceRequestBody;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.JobInfo.WriteDisposition;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableResult;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

public class BqDataTableUtils {

  private static final Pattern BQ_TABLE_PATTERN =
      Pattern.compile("^projects/([^/]+)/datasets/([^/]+)/tables/(.+)$");

  /** Updates name, description and/or referencing target of BigQuery data table reference. */
  public static GcpBigQueryDataTableResource updateBigQueryDataTableReference(
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceUuid,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      @Nullable String projectId,
      @Nullable String datasetId,
      @Nullable String tableId,
      @Nullable CloningInstructionsEnum cloningInstructions)
      throws ApiException {
    UpdateBigQueryDataTableReferenceRequestBody body =
        new UpdateBigQueryDataTableReferenceRequestBody();
    if (name != null) {
      body.setName(name);
    }
    if (description != null) {
      body.setDescription(description);
    }
    if (projectId != null) {
      body.setProjectId(projectId);
    }
    if (datasetId != null) {
      body.setDatasetId(datasetId);
    }
    if (tableId != null) {
      body.setDataTableId(tableId);
    }
    if (cloningInstructions != null) {
      body.setCloningInstructions(cloningInstructions);
    }
    return resourceApi.updateBigQueryDataTableReferenceResource(body, workspaceUuid, resourceId);
  }

  /**
   * Read and validate data populated by {@code populateBigQueryDataset} from a BigQuery dataset.
   * This is intended for validating that the provided user has read access to the given dataset.
   */
  public static TableResult readPopulatedBigQueryTable(
      GcpBigQueryDatasetResource dataset, TestUserSpecification user, String projectId)
      throws IOException, InterruptedException {

    final BigQuery bigQueryClient = ClientTestUtils.getGcpBigQueryClient(user, projectId);
    final TableId resultTableId =
        TableId.of(
            projectId, dataset.getAttributes().getDatasetId(), BqDatasetUtils.BQ_RESULT_TABLE_NAME);
    final TableResult employeeTableResult =
        bigQueryClient.query(
            QueryJobConfiguration.newBuilder(
                    "SELECT * FROM `"
                        + projectId
                        + "."
                        + dataset.getAttributes().getDatasetId()
                        + ".employee`;")
                .setDestinationTable(resultTableId)
                .setWriteDisposition(WriteDisposition.WRITE_TRUNCATE)
                .build());
    final long numRows =
        StreamSupport.stream(employeeTableResult.getValues().spliterator(), false).count();
    assertThat(numRows, is(greaterThanOrEqualTo(2L)));
    return employeeTableResult;
  }

  /**
   * Parse BigQuery table attributes from a fully-qualified GCP resource identifier string (e.g.
   * "projects/my-project/datasets/mydataset/tables/mytable").
   *
   * <p>This only parses the resource identifier string, it does not check if the provided IDs are
   * real or valid.
   */
  public static GcpBigQueryDataTableAttributes parseBqTable(String resourceIdentifier) {
    Matcher matcher = BQ_TABLE_PATTERN.matcher(resourceIdentifier);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Resource identifier "
              + resourceIdentifier
              + " does not match expected pattern for BQ table");
    }
    return new GcpBigQueryDataTableAttributes()
        .projectId(matcher.group(1))
        .datasetId(matcher.group(2))
        .dataTableId(matcher.group(3));
  }
}
