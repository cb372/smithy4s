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
package tests

import cats.Applicative
import smithy4s.kinds._

object DummyService {

  def apply[F[_]]: PartiallyApplied[F] = new PartiallyApplied[F]

  class PartiallyApplied[F[_]] {
    def create[Alg[_[_, _, _, _, _]]](implicit
        service: Service[Alg],
        F: Applicative[F]
    ): FunctorAlgebra[Alg, F] = {
      type Op[I, E, O, SI, SO] = service.Operation[I, E, O, SI, SO]
      service.fromPolyFunction[Kind1[F]#toKind5] {
        service.opToEndpoint.andThen[Kind1[F]#toKind5](
          new PolyFunction5[service.Endpoint, Kind1[F]#toKind5] {
            def apply[I, E, O, SI, SO](
                ep: Endpoint[Op, I, E, O, SI, SO]
            ): F[O] =
              F.pure(ep.output.compile(DefaultSchemaVisitor))
          }
        )
      }
    }
  }

}
