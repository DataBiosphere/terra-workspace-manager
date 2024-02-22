package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseSpringBootUnitTest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CronjobDaoTest extends BaseSpringBootUnitTest {
  @Autowired CronjobDao cronjobDao;

  @Test
  public void claimJob_claimAfterInterval_succeeds() throws InterruptedException {
    String jobName = "TEST_JOB";
    // Guarantee the job is claimed at the start of this test.
    assertTrue(cronjobDao.claimJob(jobName, Duration.ZERO));
    TimeUnit.SECONDS.sleep(5);
    // Check it's been at least 4 seconds to avoid potential flakiness
    assertTrue(cronjobDao.claimJob(jobName, Duration.ofSeconds(4)));
  }

  @Test
  public void claimJob_claimBeforeInterval_fails() throws InterruptedException {
    String jobName = "TEST_JOB";
    // Guarantee the job is claimed at the start of this test.
    assertTrue(cronjobDao.claimJob(jobName, Duration.ZERO));
    // Attempt to claim the job with time durations that haven't passed yet.
    assertFalse(cronjobDao.claimJob(jobName, Duration.ofSeconds(30)));
    // Sleep to ensure at least 5 seconds pass
    TimeUnit.SECONDS.sleep(5);
    assertFalse(cronjobDao.claimJob(jobName, Duration.ofMinutes(10)));
    assertFalse(cronjobDao.claimJob(jobName, Duration.ofHours(1)));
    // We waited 5 seconds above, so this should successfully claim the job.
    assertTrue(cronjobDao.claimJob(jobName, Duration.ofSeconds(4)));
  }

  @Test
  public void claimJob_independentJobClaims_succeed() throws InterruptedException {
    String jobName = "TEST_JOB";
    String secondJobName = "SECOND_JOB";
    // Guarantee both jobs are claimed at the start of this test.
    assertTrue(cronjobDao.claimJob(jobName, Duration.ZERO));
    assertTrue(cronjobDao.claimJob(secondJobName, Duration.ZERO));
    // One hour has not passed for either job.
    assertFalse(cronjobDao.claimJob(jobName, Duration.ofHours(1)));
    assertFalse(cronjobDao.claimJob(secondJobName, Duration.ofHours(1)));
    TimeUnit.SECONDS.sleep(5);
    assertTrue(cronjobDao.claimJob(jobName, Duration.ofSeconds(4)));
    // We cannot immediately reclaim the first job, but can claim the second job.
    assertFalse(cronjobDao.claimJob(jobName, Duration.ofSeconds(4)));
    assertTrue(cronjobDao.claimJob(secondJobName, Duration.ofSeconds(4)));
  }
}
