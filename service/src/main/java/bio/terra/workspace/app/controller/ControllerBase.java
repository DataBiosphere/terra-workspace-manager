package bio.terra.workspace.app.controller;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateUserRole;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

/**
 * Super class for controllers containing common code. The code in here requires the @Autowired
 * beans from the @Controller classes, so it is better as a superclass rather than static methods.
 */
public class ControllerBase {
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;
  private final SamService samService;

  public ControllerBase(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService) {
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
    this.samService = samService;
  }

  public SamService getSamService() {
    return samService;
  }

  public AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
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
    return String.format("%s/%s/%s", request.getServletPath(), resultWord, jobId);
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
    return jobReport.getStatus() == StatusEnum.RUNNING ? HttpStatus.ACCEPTED : HttpStatus.OK;
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
      case MANAGED_BY_APPLICATION:
        {
          // Supplying a user is optional for applications
          if (inputUser == null) {
            return new PrivateUserRole.Builder().present(false).build();
          }

          // We have a private user, so make sure the email is present and valid
          String userEmail = commonFields.getPrivateResourceUser().getUserName();
          ControllerValidationUtils.validateEmail(userEmail);

          // Validate that the assigned user is a member of the workspace. It must have at least
          // READ action.
          SamRethrow.onInterrupted(
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
          List<ControlledResourceIamRole> roles =
              commonFields.getPrivateResourceUser().getPrivateResourceIamRoles().stream()
                  .map(ControlledResourceIamRole::fromApiModel)
                  .collect(Collectors.toList());
          if (roles.isEmpty()) {
            throw new ValidationException(
                "You must specify at least one role when you specify PrivateResourceIamRoles");
          }

          // The legal options for the assigned user of an application is READER
          // or WRITER. EDITOR is not allowed. We take the "max" of READER and WRITER.
          var maxRole = ControlledResourceIamRole.READER;
          for (ControlledResourceIamRole role : roles) {
            if (role == ControlledResourceIamRole.WRITER) {
              if (maxRole == ControlledResourceIamRole.READER) {
                maxRole = role;
              }
            } else if (role != ControlledResourceIamRole.READER) {
              throw new ValidationException(
                  "For application private controlled resources, only READER and WRITER roles are allowed. Found "
                      + role.toApiModel());
            }
          }
          return new PrivateUserRole.Builder()
              .present(true)
              .userEmail(userEmail)
              .role(maxRole)
              .build();
        }

      case MANAGED_BY_USER:
        {
          // TODO: PF-1218 The target state is that supplying a user is not allowed.
          //  However, current CLI and maybe UI are supplying all or part of the structure,
          //  so tolerate all states: no-input, only roles, roles and user
          /* Target state:
          // Supplying a user is not allowed. The creating user is always the assigned user.
          validateNoInputUser(inputUser);
          */

          // Fill in the user role for the creating user
          String userEmail =
              SamRethrow.onInterrupted(
                  () -> samService.getUserEmailFromSam(userRequest), "getUserEmailFromSam");

          // TODO: PF-1218 temporarily allow user spec and make sure it matches the requesting
          //  user. Ignore the role list. If the user name is specified, then make sure it
          //  matches the requesting name.
          if (inputUser != null && inputUser.getUserName() != null) {
            if (!StringUtils.equalsIgnoreCase(userEmail, inputUser.getUserName())) {
              throw new BadRequestException(
                  "User ("
                      + userEmail
                      + ") may only assign a private controlled resource to themselves");
            }
          }

          // At this time, all private resources grant EDITOR permission to the resource user.
          // This could be parameterized if we ever have reason to grant different permissions
          // to different objects.
          return new PrivateUserRole.Builder()
              .present(true)
              .userEmail(userEmail)
              .role(ControlledResourceIamRole.EDITOR)
              .build();
        }

      default:
        throw new InternalLogicException("Unknown managedBy enum");
    }
  }

  public void validateNoInputUser(@Nullable ApiPrivateResourceUser inputUser) {
    if (inputUser != null) {
      throw new ValidationException(
          "PrivateResourceUser can only be specified by applications for private resources");
    }
  }
}
