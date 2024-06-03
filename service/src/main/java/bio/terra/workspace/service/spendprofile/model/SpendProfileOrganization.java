package bio.terra.workspace.service.spendprofile.model;

import bio.terra.profile.model.Organization;

import java.util.Map;

public record SpendProfileOrganization(boolean enterprise, Map<String, String> limits) {
  public SpendProfileOrganization(Organization organization) {
    this(organization.isEnterprise(), organization.getLimits());
  }

  public SpendProfileOrganization(boolean enterprise) {
    this(enterprise, null);
  }
}
