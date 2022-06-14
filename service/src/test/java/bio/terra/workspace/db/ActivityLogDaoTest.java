package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.BaseUnitTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ActivityLogDaoTest extends BaseUnitTest {

  @Autowired ActivityLogDao activityLogDao;

  @Test
  public void setUpdateDateAndGet() {
    String workspaceId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    activityLogDao.setUpdateDate(workspaceId, now);
    var latestDate = activityLogDao.getLastUpdateDate(workspaceId);
    assertEquals(now, latestDate);
  }

  @Test
  public void getLatestDate() {
    String workspaceId = UUID.randomUUID().toString();
    var date = Instant.now();
    var secondDate = Instant.now();
    var thirdDate = Instant.now();
    activityLogDao.setUpdateDate(workspaceId, date);
    activityLogDao.setUpdateDate(workspaceId, secondDate);
    activityLogDao.setUpdateDate(workspaceId, thirdDate);
    var latestDate = activityLogDao.getLastUpdateDate(workspaceId);
    assertEquals(thirdDate, latestDate);
  }

  @Test
  public void getLatestDate_twoWorkspace() {
    String workspaceId1 = UUID.randomUUID().toString();
    String workspaceId2 = UUID.randomUUID().toString();
    var date = Instant.now();
    var secondDate = Instant.now();
    var thirdDate = Instant.now();
    var fourthDate = Instant.now();

    activityLogDao.setUpdateDate(workspaceId1, date);
    activityLogDao.setUpdateDate(workspaceId2, secondDate);
    activityLogDao.setUpdateDate(workspaceId1, thirdDate);
    activityLogDao.setUpdateDate(workspaceId2, fourthDate);
    var latestDate1 = activityLogDao.getLastUpdateDate(workspaceId1);
    var latestDate2 = activityLogDao.getLastUpdateDate(workspaceId2);

    assertEquals(thirdDate, latestDate1);
    assertEquals(fourthDate, latestDate2);
  }
}
