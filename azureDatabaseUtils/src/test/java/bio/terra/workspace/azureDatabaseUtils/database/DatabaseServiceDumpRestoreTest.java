package bio.terra.workspace.azureDatabaseUtils.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

import bio.terra.workspace.azureDatabaseUtils.process.LocalProcessLauncher;
import bio.terra.workspace.azureDatabaseUtils.storage.BlobStorage;
import bio.terra.workspace.azureDatabaseUtils.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class DatabaseServiceDumpRestoreTest {

  static {
    // Add bouncy castle (FIPS) provider:
    java.security.Security.addProvider(
        new org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider());
  }

  static class TestableDatabaseService extends DatabaseService {
    public TestableDatabaseService(
        DatabaseDao databaseDao, Validator validator, BlobStorage blobStorage) {
      super(databaseDao, validator, blobStorage);
    }

    @Override
    public String determinePassword() {
      return "N/A for testing";
    }
  }

  private static final String predeterminedPlaintext = "MYTESTINPUTCONTENT";
  private static final String predeterminedKeyAndIvString =
      "7i61YC3yvog8U4fNXFblpK5wy/r2Wj4WnoWqrDig2BDtKbCkChTuNI5lJWU=";
  private static final String predeterminedCiphertext =
      "XMnxlzCvmt6oEnl0CFJpij+mDX1CGQzQfEtvkVhlqfGjFg==";

  @Test
  void testPgDump() throws Exception {
    DatabaseDao databaseDao = mock(DatabaseDao.class);
    Validator validator = mock(Validator.class);
    BlobStorage storage = mock(BlobStorage.class);
    LocalProcessLauncher localProcessLauncher = mock(LocalProcessLauncher.class);
    DatabaseService testDatabaseService =
        new TestableDatabaseService(databaseDao, validator, storage);

    doNothing().when(databaseDao).grantRole(any(), any());
    doNothing().when(localProcessLauncher).launchProcess(any(), any());

    ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(predeterminedPlaintext.getBytes());
    when(localProcessLauncher.getInputStream()).thenReturn(byteArrayInputStream);

    ByteArrayOutputStream blobStorageUploadStream = new ByteArrayOutputStream();
    when(storage.getBlobStorageUploadOutputStream(any(), any(), any()))
        .thenReturn(blobStorageUploadStream);

    testDatabaseService.pgDump(
        "testdb",
        "http://host.org",
        "5432",
        "testuser",
        "testfile",
        "testcontainer",
        "http://host.org",
        predeterminedKeyAndIvString,
        localProcessLauncher);

    String output = blobStorageUploadStream.toString();
    assertThat(output, equalTo(predeterminedCiphertext));
  }

  @Test
  void testPgRestore() throws Exception {
    DatabaseDao databaseDao = mock(DatabaseDao.class);
    Validator validator = mock(Validator.class);
    BlobStorage storage = mock(BlobStorage.class);
    LocalProcessLauncher localProcessLauncher = mock(LocalProcessLauncher.class);

    DatabaseService testDatabaseService =
        new TestableDatabaseService(databaseDao, validator, storage);

    doNothing().when(databaseDao).grantRole(any(), any());
    doNothing().when(localProcessLauncher).launchProcess(any(), any());

    ByteArrayOutputStream localProcessStdIn = new ByteArrayOutputStream();
    when(localProcessLauncher.getOutputStream()).thenReturn(localProcessStdIn);

    ArgumentCaptor<OutputStream> outputStreamArgumentCaptor =
        ArgumentCaptor.forClass(OutputStream.class);
    doAnswer(
            invocation -> {
              outputStreamArgumentCaptor.getValue().write(predeterminedCiphertext.getBytes());
              return null;
            })
        .when(storage)
        .streamInputFromBlobStorage(outputStreamArgumentCaptor.capture(), any(), any(), any());

    testDatabaseService.pgRestore(
        "testdb",
        "http://host.org",
        "5432",
        "testuser",
        "testfile",
        "testcontainer",
        "http://host.org",
        predeterminedKeyAndIvString,
        localProcessLauncher);

    assertThat(localProcessStdIn.toString(), equalTo(predeterminedPlaintext));
  }

  void testDumpRestoreEncryptDecryptRoundTrip(byte[] dumpContents, String key) throws Exception {
    DatabaseDao databaseDao = mock(DatabaseDao.class);
    Validator validator = mock(Validator.class);
    BlobStorage storage = mock(BlobStorage.class);
    LocalProcessLauncher localProcessLauncher = mock(LocalProcessLauncher.class);
    DatabaseService testDatabaseService =
        new TestableDatabaseService(databaseDao, validator, storage);

    doNothing().when(databaseDao).grantRole(any(), any());
    doNothing().when(localProcessLauncher).launchProcess(any(), any());

    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(dumpContents);
    when(localProcessLauncher.getInputStream()).thenReturn(byteArrayInputStream);

    ByteArrayOutputStream blobStorageUploadStream = new ByteArrayOutputStream();
    when(storage.getBlobStorageUploadOutputStream(any(), any(), any()))
        .thenReturn(blobStorageUploadStream);

    testDatabaseService.pgDump(
        "testdb",
        "http://host.org",
        "5432",
        "testuser",
        "testfile",
        "testcontainer",
        "http://host.org",
        key,
        localProcessLauncher);

    byte[] bytes = blobStorageUploadStream.toByteArray();

    ByteArrayOutputStream localProcessStdIn = new ByteArrayOutputStream();
    when(localProcessLauncher.getOutputStream()).thenReturn(localProcessStdIn);

    ArgumentCaptor<OutputStream> outputStreamArgumentCaptor =
        ArgumentCaptor.forClass(OutputStream.class);
    doAnswer(
            invocation -> {
              // Written in analogy to the AzureBlobStorage implementation:
              OutputStream toStream = outputStreamArgumentCaptor.getValue();
              try (toStream) {
                toStream.write(bytes);
                toStream.flush();
              }
              return null;
            })
        .when(storage)
        .streamInputFromBlobStorage(outputStreamArgumentCaptor.capture(), any(), any(), any());

    testDatabaseService.pgRestore(
        "testdb",
        "http://host.org",
        "5432",
        "testuser",
        "testfile",
        "testcontainer",
        "http://host.org",
        key,
        localProcessLauncher);

    localProcessStdIn.flush();
    assertThat(localProcessStdIn.toByteArray(), equalTo(dumpContents));
  }

  private String generateRandomPrintableString(int length) {
    int leftLimit = 32; // space
    int rightLimit = 126; // tilde

    return new Random()
        .ints(leftLimit, rightLimit + 1)
        .limit(length)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString();
  }

  private byte[] generateRandomBytes(int length) {
    byte[] array = new byte[length];
    new Random().nextBytes(array);
    return array;
  }

  private String generateKeyString() throws NoSuchAlgorithmException, NoSuchProviderException {
    return DatabaseService.KeyAndIv.random().toBase64();
  }

  @Test
  void testRoundTripRandomKey() throws Exception {
    testDumpRestoreEncryptDecryptRoundTrip(predeterminedPlaintext.getBytes(), generateKeyString());
  }

  @Test
  void test10000RandomPrintableCharacterRoundTripRandomKey() throws Exception {
    testDumpRestoreEncryptDecryptRoundTrip(
        generateRandomPrintableString(10003).getBytes(), generateKeyString());
  }

  @Test
  void test10000RandomBytesRoundTripRandomKey() throws Exception {
    testDumpRestoreEncryptDecryptRoundTrip(generateRandomBytes(10016), generateKeyString());
  }

  @Test
  void test0To200LengthRandomByteRoundTrips() {
    List<Integer> failedLengths = new ArrayList<>();
    int successes = 0;

    int min = 0;
    int max = 200;

    for (int testCase = min; testCase < max; testCase++) {
      try {
        testDumpRestoreEncryptDecryptRoundTrip(generateRandomBytes(testCase), generateKeyString());
        successes++;
      } catch (Exception e) {
        failedLengths.add(testCase);
      }
    }

    assertThat(successes, equalTo(max - min));
    assertThat(failedLengths, equalTo(List.of()));
  }

  @Test
  void test0To200LengthPrintableCharacterRoundTrips() {
    List<Integer> failedLengths = new ArrayList<>();
    int successes = 0;

    int min = 0;
    int max = 200;

    for (int testCase = min; testCase < max; testCase++) {
      try {
        testDumpRestoreEncryptDecryptRoundTrip(
            generateRandomPrintableString(testCase).getBytes(), generateKeyString());
        successes++;
      } catch (Exception e) {
        failedLengths.add(testCase);
      }
    }

    assertThat(successes, equalTo(max - min));
    assertThat(failedLengths, equalTo(List.of()));
  }
}
