package bio.terra.workspace.service.resource;

import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Support for cross-resource methods */
@Component
public class WsmResourceService {

  private final ResourceDao resourceDao;

  @Autowired
  public WsmResourceService(ResourceDao resourceDao) {
    this.resourceDao = resourceDao;
  }

  public List<WsmResource> enumerateResources(
      UUID workspaceUuid,
      @Nullable WsmResourceFamily cloudResourceType,
      @Nullable StewardshipType stewardshipType,
      int offset,
      int limit) {
    return resourceDao.enumerateResources(
        workspaceUuid, cloudResourceType, stewardshipType, offset, limit);
  }
}
