package bio.terra.workspace.azureDatabaseUtils.database;

import bio.terra.workspace.azureDatabaseUtils.process.LocalProcessLauncher;
import bio.terra.workspace.azureDatabaseUtils.storage.BlobStorage;
import bio.terra.workspace.azureDatabaseUtils.validation.Validator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.Mockito.*;

public class DatabaseServiceUnitTest {

  private static String plaintext = "MYTESTINPUTCONTENT";
  private static String key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
  private static String ciphertext = "hnNZyda6cTsdhH/cXMvuBd6+ZmaUOe5w05iBlKmPMGk=";

  @Test
  void testPgDump() throws Exception {
    DatabaseDao databaseDao = mock(DatabaseDao.class);
    Validator validator = mock(Validator.class);
    BlobStorage storage = mock(BlobStorage.class);
    LocalProcessLauncher localProcessLauncher = mock(LocalProcessLauncher.class);
    DatabaseService testDatabaseService = new DatabaseService(databaseDao, validator, storage);

    doNothing().when(databaseDao).grantRole(any(), any());
    doNothing().when(localProcessLauncher).launchProcess(any(), any());

    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream("MYTESTINPUTCONTENT".getBytes());
    when(localProcessLauncher.getInputStream()).thenReturn(byteArrayInputStream);

    ArgumentCaptor<InputStream> inputStreamArgumentCaptor = ArgumentCaptor.forClass(InputStream.class);
    doNothing().when(storage).streamOutputToBlobStorage(inputStreamArgumentCaptor.capture(), any(), any(), any());

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

    byte[] bytes = inputStreamArgumentCaptor.getValue().readAllBytes();
    String output = new String(bytes);
    assertThat(output, equalTo(ciphertext));
  }

  @Test
  void testPgRestore() throws Exception {
    DatabaseDao databaseDao = mock(DatabaseDao.class);
    Validator validator = mock(Validator.class);
    BlobStorage storage = mock(BlobStorage.class);
    LocalProcessLauncher localProcessLauncher = mock(LocalProcessLauncher.class);

    DatabaseService testDatabaseService = new DatabaseService(databaseDao, validator, storage);


    doNothing().when(databaseDao).grantRole(any(), any());
    doNothing().when(localProcessLauncher).launchProcess(any(), any());

    ByteArrayOutputStream localProcessStdIn = new ByteArrayOutputStream();
    when(localProcessLauncher.getOutputStream()).thenReturn(localProcessStdIn);

    ArgumentCaptor<OutputStream> outputStreamArgumentCaptor = ArgumentCaptor.forClass(OutputStream.class);
    doAnswer(invocation -> {
      outputStreamArgumentCaptor.getValue().write(ciphertext.getBytes());
      return null;
    }).when(storage).streamInputFromBlobStorage(outputStreamArgumentCaptor.capture(), any(), any(), any());

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

    assertThat(localProcessStdIn.toString(), equalTo(plaintext));
  }

  void testDumpRestoreEncryptDecryptRoundTrip(byte[] dumpContents, String key) throws Exception {
    DatabaseDao databaseDao = mock(DatabaseDao.class);
    Validator validator = mock(Validator.class);
    BlobStorage storage = mock(BlobStorage.class);
    LocalProcessLauncher localProcessLauncher = mock(LocalProcessLauncher.class);
    DatabaseService testDatabaseService = new DatabaseService(databaseDao, validator, storage);

    doNothing().when(databaseDao).grantRole(any(), any());
    doNothing().when(localProcessLauncher).launchProcess(any(), any());

    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(dumpContents);
    when(localProcessLauncher.getInputStream()).thenReturn(byteArrayInputStream);

    ArgumentCaptor<InputStream> inputStreamArgumentCaptor = ArgumentCaptor.forClass(InputStream.class);
    doNothing().when(storage).streamOutputToBlobStorage(inputStreamArgumentCaptor.capture(), any(), any(), any());

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

    byte[] bytes = inputStreamArgumentCaptor.getValue().readAllBytes();

    ByteArrayOutputStream localProcessStdIn = new ByteArrayOutputStream();
    when(localProcessLauncher.getOutputStream()).thenReturn(localProcessStdIn);

    ArgumentCaptor<OutputStream> outputStreamArgumentCaptor = ArgumentCaptor.forClass(OutputStream.class);
    doAnswer(invocation -> {
      outputStreamArgumentCaptor.getValue().write(bytes);
      return null;
    }).when(storage).streamInputFromBlobStorage(outputStreamArgumentCaptor.capture(), any(), any(), any());

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

    assertThat(localProcessStdIn.toByteArray(), equalTo(dumpContents));
  }

  private String generateRandomPrintableString(int length) {
    int leftLimit = 32; // space
    int rightLimit = 126; // tilde

    return new Random().ints(leftLimit, rightLimit + 1)
            .limit(length)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
  }

  private byte[] generateRandomBytes(int length) {
    byte[] array = new byte[length];
    new Random().nextBytes(array);
    return array;
  }

  private String generateKeyString() throws Exception {
    KeyGenerator kg;
    kg = KeyGenerator.getInstance("AES");


    kg.init(256, new SecureRandom());
    SecretKey secretKey = kg.generateKey();
    return Base64.getEncoder().encodeToString(secretKey.getEncoded());
  }

  @Test
  void testRoundTripRandomKey() throws Exception {
    testDumpRestoreEncryptDecryptRoundTrip(plaintext.getBytes(), generateKeyString());
  }

  @Test
  void test200RandomPrintableCharacterRoundTripRandomKey() throws Exception {
    testDumpRestoreEncryptDecryptRoundTrip(generateRandomPrintableString(200).getBytes(), generateKeyString());
  }

  @Test
  void test10000RandomPrintableCharacterRoundTripRandomKey() throws Exception {
    testDumpRestoreEncryptDecryptRoundTrip(generateRandomPrintableString(10000).getBytes(), generateKeyString());
  }

  @Test
  void test200RandomBytesRoundTripRandomKey() throws Exception {
    testDumpRestoreEncryptDecryptRoundTrip(generateRandomBytes(200), generateKeyString());
  }

  @Test
  void test10000RandomBytesRoundTripRandomKey() throws Exception {
    testDumpRestoreEncryptDecryptRoundTrip(generateRandomBytes(10000), generateKeyString());
  }

}
