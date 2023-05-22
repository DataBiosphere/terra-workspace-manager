package bio.terra.workspace.db;

import bio.terra.workspace.app.configuration.external.NotificationsConfig;
import bio.terra.workspace.db.exception.NotificationsException;
import bio.terra.workspace.db.model.WorkspaceDeletionNotification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationsDao {

  private static final Logger logger = LoggerFactory.getLogger(NotificationsDao.class);

  private final Publisher publisher;

  public NotificationsDao(NotificationsConfig notificationsConfig) {
    TopicName topicName =
        TopicName.of(
            notificationsConfig.getPubsubProjectName(), notificationsConfig.getPubsubTopic());

    try {
      this.publisher =
          Publisher.newBuilder(topicName)
              .setCredentialsProvider(
                  FixedCredentialsProvider.create(
                      getCreds(notificationsConfig.getCredentialsFilePath())))
              .build();
    } catch (IOException e) {
      throw new NotificationsException("Failed to initialize notifications publisher", e);
    }
  }

  private ServiceAccountCredentials getCreds(String acctPath) {
    try {
      return ServiceAccountCredentials.fromStream(new FileInputStream(acctPath));
    } catch (Exception e) {
      throw new NotificationsException(
          "Unable to load GoogleCredentials from configuration: " + acctPath, e);
    }
  }

  public void notifyWorkspaceDeletion(UUID workspaceUuid, String deletingUserId) {
    WorkspaceDeletionNotification data =
        new WorkspaceDeletionNotification(workspaceUuid, deletingUserId, OffsetDateTime.now());

    var mapper = new ObjectMapper().enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    try {
      var json = mapper.writeValueAsString(data);
      publish(ByteString.copyFromUtf8(json));
    } catch (JsonProcessingException e) {
      throw new NotificationsException("Failed serializing notification", e);
    }
  }

  private void publish(ByteString data) {

    var result = publisher.publish(PubsubMessage.newBuilder().setData(data).build());
    try {
      var msgId = result.get();
      logger.info("Published message" + msgId);
    } catch (InterruptedException | ExecutionException e) {
      throw new NotificationsException("Failed to publish message", e);
    }
  }
}
