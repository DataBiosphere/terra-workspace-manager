package bio.terra.workspace.service.spendprofile.model;

import bio.terra.profile.model.Organization;

public record SpendProfileOrganization(boolean enterprise) {
  public SpendProfileOrganization(Organization organization) {
    this(organization.isEnterprise());
  }
}
