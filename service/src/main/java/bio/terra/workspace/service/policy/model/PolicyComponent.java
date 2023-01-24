package bio.terra.workspace.service.policy.model;

import bio.terra.policy.model.TpsComponent;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.generated.model.ApiWsmPolicyComponent;

public enum PolicyComponent {
  WSM(ApiWsmPolicyComponent.WSM, TpsComponent.WSM);

  private ApiWsmPolicyComponent apiWsmPolicyComponent;
  private TpsComponent tpsComponent;

  PolicyComponent(ApiWsmPolicyComponent wsmPolicyComponent, TpsComponent tpsComponent) {
    this.apiWsmPolicyComponent = wsmPolicyComponent;
    this.tpsComponent = tpsComponent;
  }

  public ApiWsmPolicyComponent toApi() {
    return apiWsmPolicyComponent;
  }

  public static PolicyComponent fromTpsComponent(TpsComponent tpsComponent) {
    for (var component : values()) {
      if (component.tpsComponent.equals(tpsComponent)) {
        return component;
      }
    }
    throw new InternalLogicException(
        String.format(
            "Do not recognize TpsComponent %s, check if new enum should be added",
            tpsComponent.name()));
  }
}
