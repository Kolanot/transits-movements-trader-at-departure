/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors

import java.time.{LocalDateTime, OffsetDateTime}

import com.github.tomakehurst.wiremock.client.WireMock._
import config.AppConfig
import connectors.MessageConnector.EisSubmissionResult.{DownstreamBadGateway, DownstreamInternalServerError, EisSubmissionSuccessful}
import generators.ModelGenerators
import models.{DepartureId, MessageStatus, MessageType, MessageWithStatus}
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.{Configuration, Environment, Mode}
import play.api.test.Helpers.running
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

class MessageConnectorSpec
  extends AnyFreeSpec
    with MockitoSugar
    with ScalaFutures
    with Matchers
    with IntegrationPatience
    with WiremockSuite
    with ScalaCheckPropertyChecks
    with ModelGenerators
    with OptionValues {

  import MessageConnectorSpec._

  override protected def portConfigKey: String = "microservice.services.eis.port"


  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  private val messageType: MessageType = Gen.oneOf(MessageType.values).sample.value

  private val env = Environment.simple()
  private val configuration = Configuration.load(env)

  private val serviceConfig = new ServicesConfig(configuration, new RunMode(configuration, Mode.Dev))
  private val appConfig = new AppConfig(configuration, serviceConfig)

  "MessageConnector" - {

    "post" - {

      "return HttpResponse with status Accepted when when post is successful with Accepted" in {

        val messageSender = "MDTP-000000000000000000000000123-01"

        server.stubFor(
          post(urlEqualTo(postUrl))
            .withHeader("Content-Type", equalTo("application/xml"))
            .withHeader("Accept", equalTo("application/xml"))
            .withHeader("X-Message-Type", equalTo(messageType.toString))
            .withHeader("X-Message-Sender", equalTo(messageSender))
            .withRequestBody(matchingXPath("/transitRequest"))
            .willReturn(
              aResponse()
                .withStatus(202)
            )
        )
        val app = appBuilder.build()
        val connector = app.injector.instanceOf[MessageConnector]
        val postValue = MessageWithStatus(LocalDateTime.now(), messageType, <CC007A>test</CC007A>, MessageStatus.SubmissionPending, 1)
        val departureId = DepartureId(123)

        running(app) {
          val connector = app.injector.instanceOf[MessageConnector]
          val result = connector.post(departureId, postValue, OffsetDateTime.now())
          result.futureValue mustEqual EisSubmissionSuccessful
        }
      }

      "return a BAD_GATEWAY for a return code of 502" in {

        val messageSender = "MDTP-000000000000000000000000123-01"

        server.stubFor(
          post(urlEqualTo(postUrl))
            .withHeader("Content-Type", equalTo("application/xml"))
            .withHeader("Accept", equalTo("application/xml"))
            .withHeader("X-Message-Type", equalTo(messageType.toString))
            .withHeader("X-Message-Sender", equalTo(messageSender))
            .willReturn(
              aResponse()
                .withStatus(502)
            )
        )

        val postValue = MessageWithStatus(LocalDateTime.now(), messageType, <CC007A>test</CC007A>, MessageStatus.SubmissionPending, 1)
        val departureId = DepartureId(123)
        val app = appBuilder.build()

        running(app) {
          val connector = app.injector.instanceOf[MessageConnector]
          val result = connector.post(departureId, postValue, OffsetDateTime.now())
          result.futureValue mustEqual DownstreamBadGateway
        }
      }

      "return a BAD_GATEWAY for a return code of 500" in {

        val messageSender = "MDTP-000000000000000000000000123-01"

        server.stubFor(
          post(urlEqualTo(postUrl))
            .withHeader("Content-Type", equalTo("application/xml"))
            .withHeader("Accept", equalTo("application/xml"))
            .withHeader("X-Message-Type", equalTo(messageType.toString))
            .withHeader("X-Message-Sender", equalTo(messageSender))
            .willReturn(
              aResponse()
                .withStatus(500)
            )
        )

        val postValue = MessageWithStatus(LocalDateTime.now(), messageType, <CC007A>test</CC007A>, MessageStatus.SubmissionPending, 1)
        val departureId = DepartureId(123)
        val app = appBuilder.build()

        running(app) {
          val connector = app.injector.instanceOf[MessageConnector]
          val result = connector.post(departureId, postValue, OffsetDateTime.now())
          result.futureValue mustEqual DownstreamInternalServerError
        }
      }

      "return an UnexpectedHttpResonse for an error code other than 202, 400, 403, 500 and 502" in {

        val messageSender = "MDTP-000000000000000000000000123-01"

        server.stubFor(
          post(urlEqualTo(postUrl))
            .withHeader("Content-Type", equalTo("application/xml"))
            .withHeader("Accept", equalTo("application/xml"))
            .withHeader("X-Message-Type", equalTo(messageType.toString))
            .withHeader("X-Message-Sender", equalTo(messageSender))
            .willReturn(
              aResponse()
                .withStatus(418)
            )
        )

        val postValue = MessageWithStatus(LocalDateTime.now(), messageType, <CC007A>test</CC007A>, MessageStatus.SubmissionPending, 1)
        val departureId = DepartureId(123)
        val app = appBuilder.build()

        running(app) {
          val connector = app.injector.instanceOf[MessageConnector]
          val result = connector.post(departureId, postValue, OffsetDateTime.now())
          result.futureValue.statusCode mustEqual 418
        }
      }

    }
  }

}

object MessageConnectorSpec {

  private val postUrl = "/movements/messages"
  private val genFailedStatusCodes: Gen[Int] = Gen.choose(400, 599)
}
