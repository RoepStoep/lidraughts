package lidraughts.streamer

import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints

import lidraughts.common.Form.{ formatter, constraint }

object StreamerForm {

  import Streamer.{ Name, Headline, Description, Twitch, YouTube, Listed }

  lazy val emptyUserForm = Form(mapping(
    "name" -> nameField,
    "headline" -> optional(headlineField),
    "description" -> optional(descriptionField),
    "twitch" -> optional(text.verifying(
      Constraints.minLength(2),
      Constraints.maxLength(47)
    ).verifying("Invalid Twitch username", s => Streamer.Twitch.parseUserId(s).isDefined)),
    "youTube" -> optional(text.verifying("Invalid YouTube channel", s => Streamer.YouTube.parseChannelId(s).isDefined)),
    "listed" -> boolean,
    "approval" -> optional(mapping(
      "granted" -> boolean,
      "featured" -> boolean,
      "requested" -> boolean,
      "ignored" -> boolean,
      "chat" -> boolean
    )(ApprovalData.apply)(ApprovalData.unapply))
  )(UserData.apply)(UserData.unapply)
    .verifying(
      "Must specify a Twitch and/or YouTube channel.",
      u => u.twitch.isDefined || u.youTube.isDefined
    ))

  def userForm(streamer: Streamer) = emptyUserForm fill UserData(
    name = streamer.name,
    headline = streamer.headline,
    description = streamer.description,
    twitch = streamer.twitch.map(_.userId),
    youTube = streamer.youTube.map(_.channelId),
    listed = streamer.listed.value,
    approval = ApprovalData(
      granted = streamer.approval.granted,
      featured = streamer.approval.autoFeatured,
      requested = streamer.approval.requested,
      ignored = streamer.approval.ignored,
      chat = streamer.approval.chatEnabled
    ).some
  )

  case class UserData(
      name: Name,
      headline: Option[Headline],
      description: Option[Description],
      twitch: Option[String],
      youTube: Option[String],
      listed: Boolean,
      approval: Option[ApprovalData]
  ) {

    def apply(streamer: Streamer, asMod: Boolean) = {
      val newStreamer = streamer.copy(
        name = name,
        headline = headline,
        description = description,
        twitch = twitch.flatMap(Twitch.parseUserId).map(Twitch.apply),
        youTube = youTube.flatMap(YouTube.parseChannelId).map(YouTube.apply),
        listed = Listed(listed),
        updatedAt = DateTime.now
      )
      newStreamer.copy(
        approval = approval match {
          case Some(m) if asMod => streamer.approval.copy(
            granted = m.granted,
            autoFeatured = m.featured && m.granted,
            requested = !m.granted && {
              if (streamer.approval.requested != m.requested) m.requested
              else streamer.approval.requested || m.requested
            },
            ignored = m.ignored && !m.granted,
            chatEnabled = m.chat
          )
          case None => streamer.approval
        }
      )
    }
  }

  case class ApprovalData(
      granted: Boolean,
      featured: Boolean,
      requested: Boolean,
      ignored: Boolean,
      chat: Boolean
  )

  private implicit val headlineFormat = formatter.stringFormatter[Headline](_.value, Headline.apply)
  private def headlineField = of[Headline].verifying(constraint.maxLength[Headline](_.value)(300))
  private implicit val descriptionFormat = formatter.stringFormatter[Description](_.value, Description.apply)
  private def descriptionField = of[Description].verifying(constraint.maxLength[Description](_.value)(50000))
  private implicit val nameFormat = formatter.stringFormatter[Name](_.value, Name.apply)
  private def nameField = of[Name].verifying(
    constraint.minLength[Name](_.value)(3),
    constraint.maxLength[Name](_.value)(25)
  )
}
