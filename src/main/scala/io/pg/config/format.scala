package io.pg.config

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec

private object circe {
  implicit val circeConfig: Configuration = Configuration.default.withDiscriminator("kind")
}

import circe.circeConfig

@ConfiguredJsonCodec()
sealed trait TextMatcher extends Product with Serializable

object TextMatcher {
  final case class Equals(value: String) extends TextMatcher
  final case class Matches(regex: String) extends TextMatcher

}

@ConfiguredJsonCodec()
sealed trait Matcher extends Product with Serializable

object Matcher {
  final case class Author(email: TextMatcher) extends Matcher
  final case class Description(text: TextMatcher) extends Matcher
  final case class PipelineStatus(status: String) extends Matcher
  final case class Many(values: List[Matcher]) extends Matcher
}

@ConfiguredJsonCodec()
final case class Rule(name: String, matcher: Matcher)

@ConfiguredJsonCodec()
final case class ProjectConfig(rules: List[Rule])