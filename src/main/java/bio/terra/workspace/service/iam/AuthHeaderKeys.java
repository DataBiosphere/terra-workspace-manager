package bio.terra.workspace.service.iam;

public enum AuthHeaderKeys {
  // keys for auth headers
  OIDC_ACCESS_TOKEN("OIDC_ACCESS_token"),
  AUTHORIZATION("Authorization"),
  OIDC_CLAIM_EMAIL("OIDC_CLAIM_email"),
  OIDC_CLAIM_USER_ID("OIDC_CLAIM_user_id");

  private final String keyName;

  AuthHeaderKeys(String keyName) {
    this.keyName = keyName;
  }

  public String getKeyName() {
    return keyName;
  }
}
