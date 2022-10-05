package bio.terra.workspace.service.spendprofile.client;

import bio.terra.profile.api.ProfileApi;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;

public interface BpmClientProvider {
  ProfileApi getProfileApi(AuthenticatedUserRequest userRequest);
}
