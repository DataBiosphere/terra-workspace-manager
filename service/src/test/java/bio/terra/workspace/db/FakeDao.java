package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * A fake DAO class containing methods annotated with {@link ReadTransaction} and {@link
 * WriteTransaction}. This is used to test that transaction management and DB retry management are
 * properly configured.
 */
@Component
public class FakeDao {

  private final AtomicInteger count = new AtomicInteger(0);

  @ReadTransaction
  public void throwMeRead(Exception e) throws Exception {
    count.incrementAndGet();
    throw e;
  }

  @WriteTransaction
  public void throwMeWrite(Exception e) throws Exception {
    count.incrementAndGet();
    throw e;
  }

  public int getCount() {
    return count.get();
  }

  public void reset() {
    count.set(0);
  }
}
