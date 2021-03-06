/*
 * Copyright 2019 akka-fusion.com
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

package fusion.discoveryx.server.config

import akka.actor.testkit.typed.scaladsl.{ ActorTestKit, ScalaTestWithActorTestKit }
import akka.actor.typed.scaladsl.AskPattern._
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import fusion.common.config.FusionConfigFactory
import fusion.core.extension.FusionCore
import fusion.discoveryx.common.Constants
import fusion.discoveryx.model._
import fusion.discoveryx.server.protocol.ConfigEntityCommand
import fusion.discoveryx.server.util.ProtobufJson4s
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Await
import scala.concurrent.duration._

class ConfigEntityTest
    extends ScalaTestWithActorTestKit(
      ActorTestKit(
        Constants.DISCOVERYX,
        FusionConfigFactory.arrangeConfig(ConfigFactory.load("application-test.conf"), Constants.DISCOVERYX, "akka")))
    with AnyWordSpecLike {
  override implicit def timeout: Timeout = 10.seconds
  override implicit val patience: PatienceConfig = PatienceConfig(timeout.duration, 15.millis)

  FusionCore(system)
  private val configEntity = ConfigEntity.init(system)

  "ConfigEntity" must {
    val namespace = "me.yangbajing"
    val dataId = "akka"
    val content =
      """discoveryx {
      |  server {
      |    enable = false
      |  }
      |}""".stripMargin

    "PublishConfig" in {
      val in = ConfigItem(namespace, "play", content = content, `type` = ConfigType.HOCON)
      val reply = configEntity
        .ask[ConfigReply](
          replyTo =>
            ShardingEnvelope(
              ConfigEntity.makeEntityId(in.namespace, in.dataId),
              ConfigEntityCommand(replyTo, ConfigEntityCommand.Cmd.Publish(in))))
        .futureValue
      println(ProtobufJson4s.toJsonPrettyString(reply))
    }

    "GetConfig" in {
      val reply = configEntity
        .ask[ConfigReply](
          replyTo =>
            ShardingEnvelope(
              ConfigEntity.makeEntityId(namespace, dataId),
              ConfigEntityCommand(replyTo, ConfigEntityCommand.Cmd.Get(ConfigGet()))))
        .futureValue
      println(ProtobufJson4s.toJsonPrettyString(reply))
    }

    "RemoveConfig" in {
      val in = ConfigRemove(namespace, dataId)
      val reply = configEntity
        .ask[ConfigReply](
          replyTo =>
            ShardingEnvelope(
              ConfigEntity.makeEntityId(in.namespace, in.dataId),
              ConfigEntityCommand(replyTo, ConfigEntityCommand.Cmd.Remove(in))))
        .futureValue
      println(ProtobufJson4s.toJsonPrettyString(reply))
    }

    "publishConfig" in {
      println(system.settings.config.getObject("akka-persistence-jdbc.shared-databases"))
      val future = configEntity.ask[ConfigReply] { replyTo =>
        ShardingEnvelope(
          ConfigEntity.makeEntityId(namespace, dataId),
          ConfigEntityCommand(
            replyTo,
            ConfigEntityCommand.Cmd.Publish(ConfigItem(namespace, dataId, content = content))))
      }
      val reply = Await.result(future, Duration.Inf)
      println(ProtobufJson4s.toJsonPrettyString(reply))
    }
  }
}
