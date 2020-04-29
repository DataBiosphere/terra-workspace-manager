import com.google.auth.oauth2.GoogleCredentials
import java.io.ByteArrayInputStream
import java.util.UUID

import com.typesafe.config.{Config, ConfigFactory}
import io.gatling.http.Predef._
import io.gatling.core.Predef._
import scala.collection.JavaConverters._
import sys.process._

class BasicSimulation extends Simulation {
  def vaultToken: String = {
    val userHome: String = System.getProperty("user.home")
    val vaultTokenPath: String = if (userHome.startsWith("/home/jenkins")) {
      "/etc/vault-token-dsde"
    } else {
      s"$userHome/.vault-token"
    }
    scala.io.Source.fromFile(s"$vaultTokenPath").mkString
  }
  /**
   * Get the data JSON string from the given vault path
   * @param path path in vault
   * @return JSON string at path
   */
  def getVaultJson(path: String) : String = {
    val vaultAddr = "https://clotho.broadinstitute.org:8200"
    val vaultCmd = s"docker run --cap-add IPC_LOCK --rm -e VAULT_TOKEN=${vaultToken} -e VAULT_ADDR=${vaultAddr} vault:1.1.0 vault read -format json -field data ${path}"
    vaultCmd.!!.stripLineEnd
  }
  /**
   * Given a path to a service account credential in vault, obtain an access token
   * @param onBehalfOfUser identifies the user who grants permissions to service account to do things on its behalf
   * @param serviceAccountVaultPath path to valid SA credential JSON in vault
   * @return Google API access token
   */
  def getDomainWideDelegationAccessToken(onBehalfOfUser: String, serviceAccountVaultPath: String): String = {
    val scopes = List(
      "profile",
      "email",
      "openid",
      "https://www.googleapis.com/auth/devstorage.full_control",
      "https://www.googleapis.com/auth/cloud-platform"
    )
    val serviceAccountJson = getVaultJson( serviceAccountVaultPath )
    val credentials = GoogleCredentials.fromStream(
      new ByteArrayInputStream(serviceAccountJson.getBytes())).createScoped(scopes.asJava)
      .createDelegated(onBehalfOfUser)
    credentials.refreshIfExpired()
    val newAccessToken = credentials.getAccessToken
    newAccessToken.getTokenValue
  }

  val config: Config = ConfigFactory.load("application.conf")
  val baseUrl = config.getString("dev.terra.workspaceManager")
  val user = config.getString("dev.terra.user")
  val authToken = getDomainWideDelegationAccessToken(user, "secret/dsde/firecloud/dev/common/firecloud-account.json")

  val workspaceIdFeeder = Iterator.continually(
    Map("workspaceId" -> UUID.randomUUID)
  )
  val tokenFeeder = Iterator.continually(
    Map("authToken" -> authToken)
  )

  val api : String = "api/v1/workspaces"
  val body = """
               |{
               |  "id": "${workspaceId}",
               |  "authToken": "${authToken}",
               |  "spendProfile": "${workspaceId}",
               |  "policies": [ "${workspaceId}" ]
               |}
               |""".stripMargin
  val httpConf = http.baseUrl(baseUrl)
  val scn = scenario("Create Workspace Simulation")
    .feed(workspaceIdFeeder)
    .feed(tokenFeeder)
    .exec(http("post_workspaces")
      .post(api)
      .header("Authorization", "Bearer ${authToken}")
      .body(StringBody(body)).asJson
      .check(status.is(200)))
  setUp(
    scn.inject(atOnceUsers(10))
  ).protocols(httpConf)
}
