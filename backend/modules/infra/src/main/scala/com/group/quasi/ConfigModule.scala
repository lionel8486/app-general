package com.group.quasi

import com.group.quasi.domain.infra.HttpConfig
import com.group.quasi.domain.infra.notification.{NotificationConfig, NotificationConfigs, NotificationOption}
import com.group.quasi.domain.model.users.UserConfig
import com.group.quasi.domain.persistence.repository.DBConfig
import distage.ModuleDef
import exchange.api.auth.JwtConfig
import pdi.jwt.JwtAlgorithm
import pdi.jwt.algorithms.JwtAsymmetricAlgorithm
import pureconfig.ConfigConvert.catchReadError
import pureconfig._
import pureconfig.error.{CannotConvert, ConfigReaderFailures, ExceptionThrown}
import pureconfig.generic.auto._
import pureconfig.configurable._

import java.security.KeyFactory
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.util.Base64
import scala.util.Try
class ConfigModule extends ModuleDef {
  implicit val notificationOption =  ConfigReader[String].map(catchReadError(NotificationOption.unsafe))
  implicit val notificationOptions = genericMapReader[NotificationOption, NotificationConfig](catchReadError(NotificationOption.unsafe))

  implicit val jwtConfig: ConfigReader[JwtConfig] =  ConfigReader.fromCursor[JwtConfig] { cur =>
    for{
      algo <- cur.fluent.at("algo").asString.flatMap(
        str => JwtAlgorithm.optionFromString(str)
          .filter(_.isInstanceOf[JwtAsymmetricAlgorithm])
          .map(_.asInstanceOf[JwtAsymmetricAlgorithm])
          .toRight(ConfigReaderFailures(cur.failureFor(CannotConvert(str,"JwtAsymmetricAlgorithm","")),Nil))
      )
      publicKey <- cur.fluent.at("public-key").asString.flatMap(
        str => Try(Base64.getDecoder.decode(str))
          .map(new X509EncodedKeySpec(_)).map(KeyFactory.getInstance(algo.fullName).generatePublic).toEither.left.map[ConfigReaderFailures](e=>ConfigReaderFailures(cur.failureFor(ExceptionThrown(e)),Nil))
      )
      privateKey <- cur.fluent.at("private-key").asString.flatMap(
        str => Try(Base64.getDecoder.decode(str))
          .map(new PKCS8EncodedKeySpec(_)).map(KeyFactory.getInstance(algo.fullName).generatePrivate).toEither.left.map[ConfigReaderFailures](e=>ConfigReaderFailures(cur.failureFor(ExceptionThrown(e)),Nil))
      )
    } yield {
      JwtConfig(publicKey = publicKey, privateKey = privateKey, algo = algo)
    }

  }
  make[ConfigObjectSource].fromValue(ConfigSource.default)
  make[NotificationConfigs].from((_:ConfigObjectSource).at("notification").loadOrThrow[NotificationConfigs])
  make[HttpConfig].from((_: ConfigObjectSource).at("http").loadOrThrow[HttpConfig])
  make[JwtConfig].from((_:ConfigObjectSource).at("jwt").loadOrThrow[JwtConfig])
  make[UserConfig].from((_: ConfigObjectSource).at("user").loadOrThrow[UserConfig])
  make[DBConfig].from((_: ConfigObjectSource).at("db").loadOrThrow[DBConfig])
}
