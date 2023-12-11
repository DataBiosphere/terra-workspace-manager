package bio.terra.workspace.service.iam;

import jakarta.servlet.http.HttpServletRequest;

// Making this an interface as I'm not sure what request authentication will look like in mc-terra.
public interface AuthenticatedUserRequestFactory {

  AuthenticatedUserRequest from(HttpServletRequest servletRequest);
}
