package bio.terra.workspace.service.iam;

import bio.terra.common.exception.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Optional;
import java.util.UUID;

public class AuthenticatedUserRequest {
  public enum AuthType {
    OIDC,
    BEARER,
    BASIC,
    NONE
  }

  private String email;
  private String subjectId;
  private Optional<String> token;
  private UUID reqId;
  private AuthType authType;

  public AuthenticatedUserRequest() {
    this.reqId = UUID.randomUUID();
    this.token = Optional.empty();
  }

  public AuthenticatedUserRequest(String email, String subjectId, Optional<String> token) {
    this.email = email;
    this.subjectId = subjectId;
    this.token = token;
  }

  public String getSubjectId() {
    return subjectId;
  }

  public AuthenticatedUserRequest subjectId(String subjectId) {
    this.subjectId = subjectId;
    return this;
  }

  public String getEmail() {
    return email;
  }

  public AuthenticatedUserRequest email(String email) {
    this.email = email;
    return this;
  }

  public Optional<String> getToken() {
    return token;
  }

  public AuthenticatedUserRequest token(Optional<String> token) {
    this.token = token;
    return this;
  }

  @JsonIgnore
  public String getRequiredToken() {
    if (token.isEmpty()) {
      throw new ApiException("Token required");
    }
    return token.get();
  }

  public UUID getReqId() {
    return reqId;
  }

  public AuthenticatedUserRequest reqId(UUID reqId) {
    this.reqId = reqId;
    return this;
  }

  public AuthType getAuthType() {
    return authType;
  }

  public AuthenticatedUserRequest authType(AuthType authType) {
    this.authType = authType;
    return this;
  }

  @Override
  public String toString() {
    return String.format(
        "AuthenticatedUserRequest%n\tEmail: %s%n\tSubject ID: %s%n\tToken: %s%n\tRequest ID: %s%n",
        Optional.ofNullable(getEmail()).orElse("null"),
        Optional.ofNullable(getSubjectId()).orElse("null"),
        getToken().map(t -> "REDACTED (" + t.length() + " chars)").orElse("null"),
        Optional.ofNullable(reqId).map(UUID::toString).orElse("null"));
  }
}
