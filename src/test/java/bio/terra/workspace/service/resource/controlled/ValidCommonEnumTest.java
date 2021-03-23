package bio.terra.workspace.service.resource.controlled;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.exception.SerializationException;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import org.junit.jupiter.api.Test;

public class ValidCommonEnumTest extends BaseUnitTest {

  @Test
  public void accessScopeValidityTest() throws Exception {
    assertThat(
        AccessScopeType.fromApi(ApiControlledResourceCommonFields.AccessScopeEnum.PRIVATE_ACCESS),
        equalTo(AccessScopeType.ACCESS_SCOPE_PRIVATE));

    assertThat(
        AccessScopeType.fromApi(ApiControlledResourceCommonFields.AccessScopeEnum.SHARED_ACCESS),
        equalTo(AccessScopeType.ACCESS_SCOPE_SHARED));

    AccessScopeType x = AccessScopeType.fromSql(AccessScopeType.ACCESS_SCOPE_PRIVATE.toSql());
    assertThat(x, equalTo(AccessScopeType.ACCESS_SCOPE_PRIVATE));

    x = AccessScopeType.fromSql(AccessScopeType.ACCESS_SCOPE_SHARED.toSql());
    assertThat(x, equalTo(AccessScopeType.ACCESS_SCOPE_SHARED));

    assertThrows(MissingRequiredFieldException.class, () -> AccessScopeType.fromApi(null));

    assertThrows(SerializationException.class, () -> AccessScopeType.fromSql("xyzzy"));
  }

  @Test
  public void managedByValidityTest() throws Exception {
    assertThat(
        ManagedByType.fromApi(ApiControlledResourceCommonFields.ManagedByEnum.APPLICATION),
        equalTo(ManagedByType.MANAGED_BY_APPLICATION));

    assertThat(
        ManagedByType.fromApi(ApiControlledResourceCommonFields.ManagedByEnum.USER),
        equalTo(ManagedByType.MANAGED_BY_USER));

    ManagedByType x = ManagedByType.fromSql(ManagedByType.MANAGED_BY_APPLICATION.toSql());
    assertThat(x, equalTo(ManagedByType.MANAGED_BY_APPLICATION));

    x = ManagedByType.fromSql(ManagedByType.MANAGED_BY_USER.toSql());
    assertThat(x, equalTo(ManagedByType.MANAGED_BY_USER));

    assertThrows(MissingRequiredFieldException.class, () -> ManagedByType.fromApi(null));

    assertThrows(SerializationException.class, () -> ManagedByType.fromSql("xyzzy"));
  }
}
