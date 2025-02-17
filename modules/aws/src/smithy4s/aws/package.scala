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

package smithy4s

import cats.{ApplicativeThrow, MonadThrow}
import smithy4s.kinds._
import smithy4s.aws.AwsOperationKind.Unary.unary

package object aws {

  type Timestamp = smithy4s.Timestamp
  val Timestamp = smithy4s.Timestamp
  type AwsRegion = aws.kernel.AwsRegion
  val AwsRegion = aws.kernel.AwsRegion
  type AwsCredentials = aws.kernel.AwsCredentials
  val AwsCredentials = aws.kernel.AwsCredentials

  type AwsClient[Alg[_[_, _, _, _, _]], F[_]] = Alg[AwsCall[F, *, *, *, *, *]]

  private[aws] def utf8String[F[_]: MonadThrow](bytes: Array[Byte]): F[String] =
    MonadThrow[F].catchNonFatal(new String(bytes, "UTF-8"))

  // format: off
  private[aws] def simplify[Alg[_[_, _, _, _, _]], F[_]:ApplicativeThrow](service: Service[Alg]): service.Interpreter[AwsCall[F, *, *, *, *, *]] => service.FunctorInterpreter[F] = {
    interpreter =>
     new PolyFunction5[service.Operation, Kind1[F]#toKind5] {
      override def apply[I, E, O, SI, SO ](op: service.Operation[I, E, O, SI, SO ]): F[O] = {
  // format: on
          val endpoint = service.opToEndpoint(op)
          (endpoint.streamedInput, endpoint.streamedOutput) match {
            case (StreamingSchema.NoStream, StreamingSchema.NoStream) =>
              interpreter(op).run
            case _ =>
              ApplicativeThrow[F].raiseError(
                new IllegalAccessException(
                  "attempting to call a streaming operation with a non-streaming client"
                )
              )
          }
        }
      }
  }
}
