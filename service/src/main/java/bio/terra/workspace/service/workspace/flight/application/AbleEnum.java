package bio.terra.workspace.service.workspace.flight.application;

public enum AbleEnum {
  ENABLE,
  DISABLE;

  public AbleEnum toggle() {
    return this == ENABLE ? DISABLE : ENABLE;
  }
}
