/*
 * (C) Copyright 2023 Nuxeo (http://nuxeo.com/) and contributors.
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
 *
 * Contributors:
 *     Delbosc Benoit
 */
package org.nuxeo.cap.bench

import io.gatling.core.Predef._
import io.gatling.http.Predef._

object ScnFullGC {

  def get = () => {
    scenario("FullGC")
      .feed(Feeders.admins)
      .exec(NuxeoBulk.versionFullGC()).exitHereIfFailed
      .exec(session => {Redis.set("versionsTotal", session("versionsTotal").as[String])
                        session})
      .exec(session => {Redis.set("versionsRetained", session("versionsRetained").as[String])
                        session})
      .exec(NuxeoBulk.binaryFullGC()).exitHereIfFailed
      .exec(session => {Redis.set("binariesTotal", session("binariesTotal").as[String])
                        session})
      .exec(session => {Redis.set("binariesRetained", session("binariesRetained").as[String])
                        session})
  }
}

class Sim90FullGC extends Simulation {
  val httpProtocol = http
    .baseUrl(Parameters.getBaseUrl())
    .disableWarmUp
    .acceptEncodingHeader("gzip, deflate")
    .connectionHeader("keep-alive")
  val scn = ScnFullGC.get()
  setUp(scn.inject(atOnceUsers(1)))
    .protocols(httpProtocol)
}
