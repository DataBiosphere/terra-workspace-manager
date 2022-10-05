package bio.terra.workspace.service.spendprofile.client;

import bio.terra.profile.api.ProfileApi;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;

/** Knows how to instantiate Billing Profile Manager clients */
public interface BpmClientProvider {
  ProfileApi getProfileApi(AuthenticatedUserRequest userRequest);
}
