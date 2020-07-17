import com.google.auth.oauth2.GoogleCredentials
import java.io.ByteArrayInputStream
import java.util.UUID
import com.typesafe.config.{Config, ConfigFactory}
import io.gatling.http.Predef._
import io.gatling.core.Predef._
import scala.collection.JavaConverters._
import sys.process._

class CreateWorkspaceSimulation extends Simulation {
  val config: Config = ConfigFactory.load("application.conf")
  val wsmBaseUrl = System.getenv(
    config.getString("dev.terra.env.wsmBaseUrl")
  )
  val email = config.getString("dev.sam.email")
  val CICD = System.getenv("CICD")

  def GHA(): (Option[String], Option[Int]) = {
    var serviceAccountFilePath = System.getenv(
      config.getString("dev.sam.firecloudServiceAccountPath")
    )
    var bufferedSource = scala.io.Source.fromFile(serviceAccountFilePath)
    var serviceAccountJson = bufferedSource.getLines.mkString
    bufferedSource.close
    var concurrency = System.getenv("GATLING_CONCURRENCY").toInt
    (Some(serviceAccountJson), Some(concurrency))
  }

  def Skaffold_Helm(): (Option[String], Option[Int]) = {
    var serviceAccountJson = System.getenv(
      config.getString("dev.sam.firecloudServiceAccount")
    )
    var concurrency = System.getenv("GATLING_CONCURRENCY").toInt
    (Some(serviceAccountJson), Some(concurrency))
  }

  val values: (Option[String], Option[Int]) = CICD match {
    case "Skaffold_Helm" => Skaffold_Helm()
    case "GHA" => GHA()
    case _ => (None, None)
  }

  val serviceAccountJson: String = values._1 match {
    case Some(s: String) => s
    case None => throw new Exception("Can't find service account json")
  }

  val concurrency: Int = values._2 match {
    case Some(i: Int) => i
    case None => 10
  }

  val scopes = List(
    "profile",
    "email",
    "openid"
    //"https://www.googleapis.com/auth/devstorage.full_control",
    //"https://www.googleapis.com/auth/cloud-platform"
  )
  val credentials =
    GoogleCredentials.fromStream(
      new ByteArrayInputStream(serviceAccountJson.getBytes()))
      .createScoped(scopes.asJava)
      .createDelegated(email)

  credentials.refreshIfExpired()
  val newAccessToken = credentials.getAccessToken
  val authToken = newAccessToken.getTokenValue

  val workspaceIdFeeder = Iterator.continually(
    Map("workspaceId" -> UUID.randomUUID)
  )
  val tokenFeeder = Iterator.continually(
    Map("authToken" -> authToken)
  )

  val api : String = "api/workspaces/v1"
  val body = """
               |{
               |  "id": "${workspaceId}",
               |  "authToken": "${authToken}",
               |  "spendProfile": "${workspaceId}",
               |  "policies": [ "${workspaceId}" ]
               |}
               |""".stripMargin

  val httpConf = http.baseUrl(wsmBaseUrl)
  val scn = scenario("Create Workspace Simulation")
    .feed(workspaceIdFeeder)
    .feed(tokenFeeder)
    .exec(http("post_workspaces")
      .post(api)
      .header("Authorization", "Bearer ${authToken}")
      .body(StringBody(body)).asJson
      .check(status.is(200)))
  setUp(
    scn.inject(atOnceUsers(concurrency))
  ).protocols(httpConf)
}