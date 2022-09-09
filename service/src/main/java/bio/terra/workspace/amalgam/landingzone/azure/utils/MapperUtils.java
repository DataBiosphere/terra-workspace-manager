package bio.terra.workspace.amalgam.landingzone.azure.utils;

import bio.terra.landingzone.job.model.ErrorReport;
import bio.terra.landingzone.job.model.JobReport;
import bio.terra.landingzone.model.LandingZoneTarget;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneParameter;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiLandingZoneTarget;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MapperUtils {

  public class LandingZoneMapper {
    private LandingZoneMapper() {}

    public static HashMap<String, String> landingZoneParametersFrom(
        List<ApiAzureLandingZoneParameter> parametersList) {
      return nullSafeListToStream(parametersList)
          .flatMap(Stream::ofNullable)
          .collect(
              Collectors.toMap(
                  ApiAzureLandingZoneParameter::getKey,
                  ApiAzureLandingZoneParameter::getValue,
                  (prev, next) -> prev,
                  HashMap::new));
    }

    private static <T> Stream<T> nullSafeListToStream(Collection<T> collection) {
      return Optional.ofNullable(collection).stream().flatMap(Collection::stream);
    }
  }

  public class JobReportMapper {
    private JobReportMapper() {}

    public static ApiJobReport from(JobReport jobReport) {
      return new ApiJobReport()
          .id(jobReport.getId())
          .description(jobReport.getDescription())
          .status(ApiJobReport.StatusEnum.valueOf(jobReport.getStatus().toString()))
          .statusCode(jobReport.getStatusCode())
          .submitted(jobReport.getSubmitted())
          .completed(jobReport.getCompleted())
          .resultURL(jobReport.getResultURL());
    }
  }

  public class ErrorReportMapper {
    private ErrorReportMapper() {}

    public static ApiErrorReport from(ErrorReport errorReport) {
      if (errorReport == null) {
        return null;
      }
      return new ApiErrorReport()
          .message(errorReport.getMessage())
          .statusCode(errorReport.getStatusCode())
          .causes(errorReport.getCauses());
    }
  }

  public class AzureCloudContextMapper {
    private AzureCloudContextMapper() {}

    public static LandingZoneTarget from(ApiLandingZoneTarget apiLandingZoneTarget) {
      return new LandingZoneTarget(
          apiLandingZoneTarget.getTenantId(),
          apiLandingZoneTarget.getSubscriptionId(),
          apiLandingZoneTarget.getResourceGroupId());
    }
  }
}
