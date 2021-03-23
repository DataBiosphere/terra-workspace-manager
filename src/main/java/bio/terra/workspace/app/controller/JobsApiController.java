package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.JobsApi;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.job.JobService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class JobsApiController implements JobsApi {

  private final JobService jobService;
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;

  @Autowired
  public JobsApiController(
      JobService jobService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request) {
    this.jobService = jobService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  @Override
  public ResponseEntity<ApiJobReport> retrieveJob(@PathVariable("id") String id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ApiJobReport jobReport = jobService.retrieveJob(id, userReq);
    return new ResponseEntity<>(jobReport, HttpStatus.valueOf(jobReport.getStatusCode()));
  }
}
