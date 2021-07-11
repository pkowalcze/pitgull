package io.pg.transport

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.generic.extras.semiauto.deriveEnumerationCodec
import io.circe.Codec

private object CirceConfiguration {
  implicit val circeConfig: Configuration = Configuration.default.withDiscriminator("@type")
}

import CirceConfiguration._

@ConfiguredJsonCodec
final case class MergeRequestState(
  projectId: Long,
  mergeRequestIid: Long,
  authorUsername: String,
  description: Option[String],
  status: MergeRequestState.Status,
  mergeability: MergeRequestState.Mergeability,
  headSha: Option[String]
)

object MergeRequestState {
  @ConfiguredJsonCodec
  sealed trait Status extends Product with Serializable

  object Status {
    case object Success extends Status
    final case class Other(value: String) extends Status
  }

  sealed trait Mergeability extends Product with Serializable

  object Mergeability {
    case object CanMerge extends Mergeability
    case object NeedsRebase extends Mergeability
    case object HasConflicts extends Mergeability

    implicit val codec: Codec[Mergeability] = deriveEnumerationCodec
  }

}
