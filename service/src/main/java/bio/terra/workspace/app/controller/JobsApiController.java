package bio.terra.workspace.app.controller;

import bio.terra.stairway.FlightState;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.generated.controller.JobsApi;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.job.JobService;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class JobsApiController implements JobsApi {

  private final JobService jobService;
  private final JobApiUtils jobApiUtils;
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;

  @Autowired
  public JobsApiController(
      JobService jobService,
      JobApiUtils jobApiUtils,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request) {
    this.jobService = jobService;
    this.jobApiUtils = jobApiUtils;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiJobReport> retrieveJob(@PathVariable("jobId") String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest);
    FlightState flightState = jobService.retrieveJob(jobId);
    ApiJobReport jobReport = jobApiUtils.mapFlightStateToApiJobReport(flightState);
    return new ResponseEntity<>(jobReport, HttpStatus.valueOf(jobReport.getStatusCode()));
  }
}
