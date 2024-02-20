package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.retry.transaction.TransactionRetryProperties;
import bio.terra.workspace.common.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotSerializeTransactionException;

public class DbRetryTest extends BaseUnitTest {

  @Autowired FakeDao fakeDao;
  @Autowired TransactionRetryProperties retryProperties;

  private static final CannotSerializeTransactionException RETRY_EXCEPTION =
      new CannotSerializeTransactionException("test");

  @BeforeEach
  public void setup() {
    fakeDao.reset();
  }

  @Test
  void retryWriteTransaction() {
    assertThrows(RETRY_EXCEPTION.getClass(), () -> fakeDao.throwMeWrite(RETRY_EXCEPTION));
    assertEquals(retryProperties.getFastRetryMaxAttempts(), fakeDao.getCount());
  }

  @Test
  void retryReadTransaction() {
    assertThrows(RETRY_EXCEPTION.getClass(), () -> fakeDao.throwMeRead(RETRY_EXCEPTION));
    assertEquals(retryProperties.getFastRetryMaxAttempts(), fakeDao.getCount());
  }
}
