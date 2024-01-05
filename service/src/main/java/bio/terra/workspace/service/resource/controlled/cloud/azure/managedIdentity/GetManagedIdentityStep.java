package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.stairway.FlightContext;
import bio.terra.workspace.common.utils.FlightUtils;
import com.azure.resourcemanager.msi.models.Identity;

public interface GetManagedIdentityStep {
  String MANAGED_IDENTITY_PRINCIPAL_ID = "MANAGED_IDENTITY_PRINCIPAL_ID";
  String MANAGED_IDENTITY_CLIENT_ID = "MANAGED_IDENTITY_CLIENT_ID";
  String MANAGED_IDENTITY_NAME = "MANAGED_IDENTITY_NAME";

  static String getManagedIdentityPrincipalId(FlightContext context) {
    return FlightUtils.getRequired(
        context.getWorkingMap(), MANAGED_IDENTITY_PRINCIPAL_ID, String.class);
  }

  static String getManagedIdentityClientId(FlightContext context) {
    return FlightUtils.getRequired(
        context.getWorkingMap(), MANAGED_IDENTITY_CLIENT_ID, String.class);
  }

  static String getManagedIdentityName(FlightContext context) {
    return FlightUtils.getRequired(context.getWorkingMap(), MANAGED_IDENTITY_NAME, String.class);
  }

  static boolean managedIdentityExistsInFlightWorkingMap(FlightContext context) {
    return context.getWorkingMap().containsKey(MANAGED_IDENTITY_NAME);
  }

  default void putManagedIdentityInContext(FlightContext context, Identity identity) {
    context.getWorkingMap().put(MANAGED_IDENTITY_PRINCIPAL_ID, identity.principalId());
    context.getWorkingMap().put(MANAGED_IDENTITY_CLIENT_ID, identity.clientId());
    context.getWorkingMap().put(MANAGED_IDENTITY_NAME, identity.name());
  }
}
