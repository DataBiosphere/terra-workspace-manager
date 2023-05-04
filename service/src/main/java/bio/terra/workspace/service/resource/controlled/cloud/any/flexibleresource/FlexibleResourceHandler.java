package bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import org.springframework.stereotype.Component;

@Component
public class FlexibleResourceHandler implements WsmResourceHandler {
  private static FlexibleResourceHandler theHandler;

  public static FlexibleResourceHandler getHandler() {
    if (theHandler == null) {
      theHandler = new FlexibleResourceHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    FlexibleResourceAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), FlexibleResourceAttributes.class);
    return ControlledFlexibleResource.builder()
        .typeNamespace(attributes.getTypeNamespace())
        .type(attributes.getType())
        .data(attributes.getData())
        .common(new ControlledResourceFields(dbResource))
        .build();
  }
}
