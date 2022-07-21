package bio.terra.workspace.service.lzm;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AzureLandingZoneService {
  private static final Logger logger = LoggerFactory.getLogger(AzureLandingZoneService.class);

  //  private final LandingZoneDao landingZoneDao;
  //
  //  @Autowired
  //  public AzureLandingZoneService(LandingZoneDao landingZoneDao) {
  //    this.landingZoneDao = landingZoneDao;
  //  }

  public List<Object> listLandingZone() throws Exception {
    logger.error("Not implemented yet");
    throw new Exception("Not implemented yet");
  }

  public void createLandingZone(String factory, String version) {
    logger.info("Creating new Azure landing zone...");
  }

  public void deleteLandingZone() throws Exception {
    throw new Exception("Not implemented yet");
  }

  public List<Object> listResourcesByPurpose(Object purpose) throws Exception {
    throw new Exception("Not implemented yet");
  }
}
