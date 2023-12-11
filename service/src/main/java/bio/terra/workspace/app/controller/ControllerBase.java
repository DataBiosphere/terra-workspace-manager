package bio.terra.workspace.app.controller;

import bio.terra.common.exception.ValidationException;
import bio.terra.common.iam.SamUser;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.ControllerUtils;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateUserRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.http.HttpStatus;

/**
 * Super class for controllers containing common code.
 *
 * <p>NOTE: I started to migrate this to a separate ControllerUtils class rather than use the class
 * hierarchy. Using a super class was based on an incorrect understanding of how HttpServletRequest
 * is handled; it is not necessary. I only did as much of this refactoring as I needed for the
 * workspace/v2 work, leaving the rest of it for later.
 *
 * <p>Making it a separate utility class lets us decompose controller modules (which are getting
 * large) into smaller pieces.
 */
public class ControllerBase {
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;
  protected final SamService samService;
  protected final FeatureConfiguration features;
  protected final FeatureService featureService;
  protected final JobService jobService;
  protected final JobApiUtils jobApiUtils;

  public ControllerBase(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      FeatureConfiguration features,
      FeatureService featureService,
      JobService jobService,
      JobApiUtils jobApiUtils) {
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
    this.samService = samService;
    this.features = features;
    this.featureService = featureService;
    this.jobService = jobService;
    this.jobApiUtils = jobApiUtils;
  }

  public AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  public SamUser getSamUser() {
    return samService.getSamUser(request);
  }

  /**
   * Returns the result endpoint corresponding to an async request, prefixed with a / character. The
   * endpoint is used to build a ApiJobReport. This method generates a result endpoint with the form
   * {servletpath}/{resultWord}/{jobId} relative to the async endpoint.
   *
   * <p>Sometimes we have more than one async endpoint with the same prefix, so need to distinguish
   * them with different result words. For example, "update-result".
   *
   * @param jobId the job id
   * @param resultWord the path component identifying the result
   * @return a string with the result endpoint URL
   */
  public String getAsyncResultEndpoint(String jobId, String resultWord) {
    return ControllerUtils.getAsyncResultEndpoint(request, resultWord, jobId);
  }

  /**
   * Returns the result endpoint corresponding to an async request where the desired path has the
   * form {servletpath}/result/{jobId}. Most of the time, the result word is "result".
   *
   * @param jobId the job id
   * @return a string with the result endpoint URL
   */
  public String getAsyncResultEndpoint(String jobId) {
    return getAsyncResultEndpoint(jobId, "result");
  }

  /**
   * Return the appropriate response code for an endpoint, given an async job report. For a job
   * that's still running, this is 202. For a job that's finished (either succeeded or failed), the
   * endpoint should return 200. More informational status codes will be included in either the
   * response or error report bodies.
   */
  public static HttpStatus getAsyncResponseCode(ApiJobReport jobReport) {
    return ControllerUtils.getAsyncResponseCode(jobReport);
  }

  /**
   * Validate and provide defaulting for the private resource user. The property is never required.
   * The only time it is allowed is for application-private resources. If it is populated, we
   * validate the user email and the specified IAM roles.
   *
   * <p>user-private resources are always assigned to the caller. You can't create a user-private
   * resource and assign it to someone else. Because we can read the caller's email from the
   * AuthenticatedUserRequest, we don't need to supply assignedUser in the request body.
   *
   * <p>application-private resources can be assigned to users other than the caller. For example,
   * Leo could call WSM to create a VM (using the Leo SA's auth token) and request it be assigned to
   * user X, not to the Leo SA.
   *
   * @param commonFields common fields from a controlled resource create request
   * @param userRequest authenticate user
   * @return PrivateUserRole holding the user email and the role list
   */
  public PrivateUserRole computePrivateUserRole(
      UUID workspaceUuid,
      ApiControlledResourceCommonFields commonFields,
      AuthenticatedUserRequest userRequest) {

    AccessScopeType accessScope = AccessScopeType.fromApi(commonFields.getAccessScope());
    ManagedByType managedBy = ManagedByType.fromApi(commonFields.getManagedBy());
    ApiPrivateResourceUser inputUser = commonFields.getPrivateResourceUser();

    // Shared access has no private user role
    if (accessScope == AccessScopeType.ACCESS_SCOPE_SHARED) {
      validateNoInputUser(inputUser);
      return new PrivateUserRole.Builder().present(false).build();
    }

    // Private access scope
    switch (managedBy) {
      case MANAGED_BY_APPLICATION -> {
        // Supplying a user is optional for applications
        if (inputUser == null) {
          return new PrivateUserRole.Builder().present(false).build();
        }

        // We have a private user, so make sure the email is present and valid
        String userEmail = commonFields.getPrivateResourceUser().getUserName();
        ControllerValidationUtils.validateEmail(userEmail);

        // Validate that the assigned user is a member of the workspace. It must have at least
        // READ action.
        Rethrow.onInterrupted(
            () ->
                samService.userIsAuthorized(
                    SamConstants.SamResource.WORKSPACE,
                    workspaceUuid.toString(),
                    SamConstants.SamWorkspaceAction.READ,
                    userEmail,
                    userRequest),
            "validate private user is workspace member");

        // Translate the incoming role list into our internal model form
        // This also validates that the incoming API model values are correct.
        var iamRole =
            ControlledResourceIamRole.fromApiModel(
                commonFields.getPrivateResourceUser().getPrivateResourceIamRole());

        if (iamRole != ControlledResourceIamRole.READER
            && iamRole != ControlledResourceIamRole.WRITER) {
          throw new ValidationException(
              "For application private controlled resources, only READER and WRITER roles are allowed. Found "
                  + iamRole.toApiModel());
        }

        return new PrivateUserRole.Builder()
            .present(true)
            .userEmail(userEmail)
            .role(iamRole)
            .build();
      }
      case MANAGED_BY_USER -> {
        validateNoInputUser(inputUser);

        // At this time, all private resources grant EDITOR permission to the resource user.
        // This could be parameterized if we ever have reason to grant different permissions
        // to different objects.
        return new PrivateUserRole.Builder()
            .present(true)
            .userEmail(samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest))
            .role(ControlledResourceIamRole.EDITOR)
            .build();
      }
      default -> throw new InternalLogicException("Unknown managedBy enum");
    }
  }

  public void validateNoInputUser(@Nullable ApiPrivateResourceUser inputUser) {
    if (inputUser != null) {
      throw new ValidationException(
          "PrivateResourceUser can only be specified by applications for private resources");
    }
  }
}
