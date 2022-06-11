/*
 *  Copyright 2021-2022 Disney Streaming
 *
 *  Licensed under the Tomorrow Open Source Technology License, Version 1.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     https://disneystreaming.github.io/TOST-1.0.txt
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package smithy4s.weavertests

import cats.effect.IO
import org.http4s.HttpApp
import org.http4s.HttpRoutes
import org.http4s.Uri
import smithy.test._
import smithy4s.Document
import smithy4s.Endpoint
import smithy4s.Service
import smithy4s.ShapeTag
import smithy4s.UnsupportedProtocolError
import weaver._

case class GeneratedTest(name: String, assertions: IO[Expectations])

abstract class WeaverTests[P, Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](
    protocolTag: ShapeTag[_],
    client: (
        HttpApp[IO],
        Uri
    ) => Either[UnsupportedProtocolError, smithy4s.Monadic[Alg, IO]],
    server: smithy4s.Monadic[Alg, IO] => Either[
      UnsupportedProtocolError,
      HttpRoutes[IO]
    ]
)(implicit service: Service[Alg, Op])
    extends SimpleIOSuite {

  private class EndpointTest[I, E, O, SE, SO](
      endpoint: Endpoint[Op, I, E, O, SE, SO]
  ) {
    def generateTests() = {
      requestTests() ++ responseTests()
    }

    private def requestTests(): List[GeneratedTest] = {
      val inputFromDocument = Document.Decoder.fromSchema(endpoint.input)
      val whrtc = new WeaverHttpRequestTestCase(client, server)

      val testCases =
        endpoint.hints
          .get(HttpRequestTests)
          .map(_.value)
          .getOrElse(Nil)
          .filter(_.protocol == protocolTag.id.toString())

      val clientTests = appliesTo(testCases)(AppliesTo.CLIENT, _.appliesTo)
        .map(whrtc.makeClientTest(endpoint, _, inputFromDocument))

      val serverTests = appliesTo(testCases)(AppliesTo.SERVER, _.appliesTo)
        .map(whrtc.makeServerTest(endpoint, _, inputFromDocument))

      serverTests ++ clientTests
    }

    private def responseTests(): List[GeneratedTest] = {
      val inputFromDocument = Document.Decoder.fromSchema(endpoint.output)
      val whrtc = new WeaverHttpResponseTestCase(client, server)

      val testCases =
        endpoint.hints
          .get(HttpResponseTests)
          .map(_.value)
          .getOrElse(Nil)
          .filter(_.protocol == protocolTag.id.toString())

      val clientTests = appliesTo(testCases)(AppliesTo.CLIENT, _.appliesTo)
        .map(whrtc.makeClientTest(endpoint, _, inputFromDocument))

      val serverTests = appliesTo(testCases)(AppliesTo.SERVER, _.appliesTo)
        .map(whrtc.makeServerTest(endpoint, _, inputFromDocument))

      serverTests ++ clientTests
    }

    private def appliesTo[TC](testCases: List[TC])(
        value: AppliesTo,
        appliesTo: TC => Option[AppliesTo]
    ): List[TC] =
      testCases.collect {
        case tc if appliesTo(tc).forall(_ == value) => tc
      }
  }

  service.service.endpoints
    .flatMap { e => new EndpointTest(e).generateTests() }
    .foreach(gt => test(gt.name)(gt.assertions))
}
