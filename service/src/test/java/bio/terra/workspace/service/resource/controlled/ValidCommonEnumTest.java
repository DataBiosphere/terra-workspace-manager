package bio.terra.workspace.service.resource.controlled;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import org.junit.jupiter.api.Test;

public class ValidCommonEnumTest extends BaseSpringBootUnitTest {

  @Test
  public void accessScopeValidityTest() {
    assertThat(
        AccessScopeType.fromApi(ApiAccessScope.PRIVATE_ACCESS),
        equalTo(AccessScopeType.ACCESS_SCOPE_PRIVATE));

    assertThat(
        AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS),
        equalTo(AccessScopeType.ACCESS_SCOPE_SHARED));

    AccessScopeType x = AccessScopeType.fromSql(AccessScopeType.ACCESS_SCOPE_PRIVATE.toSql());
    assertThat(x, equalTo(AccessScopeType.ACCESS_SCOPE_PRIVATE));

    x = AccessScopeType.fromSql(AccessScopeType.ACCESS_SCOPE_SHARED.toSql());
    assertThat(x, equalTo(AccessScopeType.ACCESS_SCOPE_SHARED));

    assertThrows(MissingRequiredFieldException.class, () -> AccessScopeType.fromApi(null));

    assertThrows(SerializationException.class, () -> AccessScopeType.fromSql("xyzzy"));

    assertThat(
        AccessScopeType.ACCESS_SCOPE_PRIVATE.toApiModel(), equalTo(ApiAccessScope.PRIVATE_ACCESS));
    assertThat(
        AccessScopeType.ACCESS_SCOPE_SHARED.toApiModel(), equalTo(ApiAccessScope.SHARED_ACCESS));
  }

  @Test
  public void managedByValidityTest() {
    assertThat(
        ManagedByType.fromApi(ApiManagedBy.APPLICATION),
        equalTo(ManagedByType.MANAGED_BY_APPLICATION));

    assertThat(ManagedByType.fromApi(ApiManagedBy.USER), equalTo(ManagedByType.MANAGED_BY_USER));

    ManagedByType x = ManagedByType.fromSql(ManagedByType.MANAGED_BY_APPLICATION.toSql());
    assertThat(x, equalTo(ManagedByType.MANAGED_BY_APPLICATION));

    x = ManagedByType.fromSql(ManagedByType.MANAGED_BY_USER.toSql());
    assertThat(x, equalTo(ManagedByType.MANAGED_BY_USER));

    assertThrows(MissingRequiredFieldException.class, () -> ManagedByType.fromApi(null));

    assertThrows(SerializationException.class, () -> ManagedByType.fromSql("xyzzy"));

    assertThat(ManagedByType.MANAGED_BY_USER.toApiModel(), equalTo(ApiManagedBy.USER));
    assertThat(
        ManagedByType.MANAGED_BY_APPLICATION.toApiModel(), equalTo(ApiManagedBy.APPLICATION));
  }
}
