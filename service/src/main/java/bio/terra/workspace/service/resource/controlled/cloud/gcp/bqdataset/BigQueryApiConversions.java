package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import com.google.api.services.bigquery.model.Dataset;
import javax.annotation.Nullable;

/** Utility methods for converting BQ dataset objects between WSM formats and GCP formats. */
public class BigQueryApiConversions {

  private BigQueryApiConversions() {}

  /**
   * Build update parameters from an existing BQ dataset object.
   *
   * @param dataset - BQ API Dataset instance
   * @return - populated update parameters object
   */
  public static ApiGcpBigQueryDatasetUpdateParameters toUpdateParameters(Dataset dataset) {
    ApiGcpBigQueryDatasetUpdateParameters updateParams =
        new ApiGcpBigQueryDatasetUpdateParameters()
            .defaultTableLifetime(fromBqExpirationTime(dataset.getDefaultTableExpirationMs()))
            .defaultPartitionLifetime(
                fromBqExpirationTime(dataset.getDefaultPartitionExpirationMs()));

    // for BQ GET dataset: null = no existing expiration time
    // for WSM PATCH BQ dataset resource: null = don't change anything, 0 = remove the existing
    // expiration time
    // since we're trying to preserve the exiting state in case we need to put it back during an
    // undo, convert an existing null expiration time to a 0, so that it gets removed again
    if (updateParams.getDefaultTableLifetime() == null) {
      updateParams.setDefaultTableLifetime(0L);
    }
    if (updateParams.getDefaultPartitionLifetime() == null) {
      updateParams.setDefaultPartitionLifetime(0L);
    }

    return updateParams;
  }

  /**
   * Helper method to convert ms -> sec, because the GCP API uses ms and the WSM API uses sec. The
   * bq command line also uses sec.
   */
  public static Long fromBqExpirationTime(@Nullable Long ms) {
    return ms == null ? null : ms / 1000;
  }

  /**
   * Helper method to convert sec -> ms, because the GCP API uses ms and the WSM API uses sec. The
   * bq command line also uses sec.
   *
   * <p>If the number of seconds is null or zero, then treat it as an undefined expiration time.
   */
  public static Long toBqExpirationTime(@Nullable Long sec) {
    return sec == null || sec == 0 ? null : sec * 1000;
  }
}
