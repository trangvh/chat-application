package org.benjp.chat.stress 
import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.jdbc.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import akka.util.duration._
import bootstrap._
import assertions._
import scala.math.abs;
import scala.util.Random;

class PublicChatFullSimulation extends Simulation {

	val httpConf = httpConfig
		.baseURL("http://www.acme.com")
		.acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
		.acceptEncodingHeader("gzip, deflate")
		.acceptLanguageHeader("fr,fr-fr;q=0.8,en-us;q=0.5,en;q=0.3")
		.connection("keep-alive")
		.userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:19.0) Gecko/20100101 Firefox/19.0")

	val scnInit = scenario("Scenario : Init")
		.exec(http("server init")
			.get("/chatServer/")
		)
		.exec(http("drop db")
			.get("/chatServer/dropDB")
			.queryParam("db", "gatling")
			.queryParam("passphrase", "chat")
		)
    .pause(500 milliseconds)
		.exec(http("init db")
			.get("/chatServer/initDB")
			.queryParam("db", "gatling")
			.queryParam("passphrase", "chat")
		)
    .pause(500 milliseconds)
    .exec(http("ensure indexes")
      .get("/chatServer/ensureIndexes")
      .queryParam("db", "gatling")
      .queryParam("passphrase", "chat")
    )
    .pause(500 milliseconds)
		.repeat(300, "userId") {
			exec(http("create user")
				.get("/chatServer/createDemoUser")
				.queryParam("username", "user${userId}")
				.queryParam("passphrase", "chat")
			)			
		}

	val iUser = new java.util.concurrent.atomic.AtomicInteger(0)
  val random = new scala.util.Random

	val scnSendMessages = scenario("Scenario : Messages")
		.exec((session:Session) => session.setAttribute("userId", iUser.getAndIncrement))
		.exec(http("get token")
			.get("/chatServer/getToken")
			.queryParam("username", "user${userId}")
			.queryParam("passphrase", "chat")
						//  "token": "45b2b",
			.check(regex("""token": "(.+)",""").saveAs("token"))
		)
    .repeat(3, "cptRoom") {
      exec((session:Session) => session.setAttribute("targetUserId", scala.math.abs(random.nextInt(200))))
      .exec(http("get room")
        .get("/chatServer/getRoom")
        .queryParam("user", "user${userId}")
        .queryParam("token", "${token}")
        .queryParam("targetUser", "user${targetUserId}")
        .queryParam("isAdmin", "false")
        .check(regex("""(.+)""").saveAs("room"))
      )
      .during(2 minutes, "cpt") {
        randomSwitch(
          20 ->
            exec(http("send message")
              .get("/chatServer/send")
              .queryParam("user", "user${userId}")
              .queryParam("targetUser", "user${targetUserId}")
              .queryParam("token", "${token}")
              .queryParam("room", "${room}")
              .queryParam("message", "This is gatlin message : ${cpt}")
            )
              .pause(0 milliseconds, 10 milliseconds)
          ,
          20 ->
            exec(http("who is online")
              .get("/chatServer/whoIsOnline")
              .queryParam("user", "user${userId}")
              .queryParam("token", "${token}")
              .queryParam("filter", "")
              .queryParam("withUsers", "false")
              .queryParam("withSpaces", "true")
              .queryParam("withPublic", "true")
              .queryParam("isAdmin", "false")
            )
              .pause(0 milliseconds, 10 milliseconds)
          ,
          1 ->
            exec(http("toggle favorite")
              .get("/chatServer/toggleFavorite")
              .queryParam("user", "user${userId}")
              .queryParam("targetUser", "user${targetUserId}")
              .queryParam("token", "${token}")
            )
              .pause(0 milliseconds, 10 milliseconds)
          ,
          3 ->
            exec(http("get status")
              .get("/chatServer/getStatus")
              .queryParam("user", "user${userId}")
              .queryParam("token", "${token}")
            )
              .pause(0 milliseconds, 10 milliseconds)
          ,
          1 ->
            exec(http("set status")
              .get("/chatServer/setStatus")
              .queryParam("user", "user${userId}")
              .queryParam("token", "${token}")
              .queryParam("status", "away")
            )
              .pause(0 milliseconds, 10 milliseconds)
          ,
          5 ->
            exec(http("update unread")
              .get("/chatServer/updateUnreadMessages")
              .queryParam("user", "user${userId}")
              .queryParam("token", "${token}")
              .queryParam("room", "${room}")
            )
              .pause(0 milliseconds, 10 milliseconds)
          ,
          50 ->
            exec(http("read message")
              .get("/chatServer/send")
              .queryParam("user", "user${userId}")
              .queryParam("targetUser", "user${targetUserId}")
              .queryParam("token", "${token}")
              .queryParam("room", "${room}")
            )
              .pause(0 milliseconds, 10 milliseconds)
        )

      }

    }

	setUp(
		scnInit.users(1).protocolConfig(httpConf)
    , scnSendMessages.users(200).ramp(20).delay(10).protocolConfig(httpConf)
		)
}