package controllers

import com.typesafe.config.ConfigFactory
import javax.inject._
import services.MsgHandler
import play.api.data.Form
import play.api.mvc._

import scala.concurrent.Future

case class MsgFormInput(
                         ToCountry: Option[String],
                         ToState: Option[String],
                         SmsMessageSid: Option[String],
                         NumMedia: Option[Int],
                         ToCity: Option[String],
                         FromZip: Option[String],
                         SmsSid: Option[String],
                         FromState: Option[String],
                         SmsStatus: Option[String],
                         FromCity: Option[String],
                         Body: String,
                         FromCountry: Option[String],
                         To: String,
                         ToZip: Option[String],
                         NumSegments: Option[String],
                         MessageSid: Option[String],
                         AccountSid: Option[String],
                         From: String,
                         ApiVersion: Option[String]
                       )

@Singleton
class HomeController @Inject()(cc: ControllerComponents,
                               handler: MsgHandler)
                              extends AbstractController(cc)
                                with play.api.i18n.I18nSupport {

  implicit lazy val executionContext = defaultExecutionContext

  val config = ConfigFactory.load()

  private val form: Form[MsgFormInput] = {
    import play.api.data.Forms._

    Form(
      mapping(
        "ToCountry" -> optional(text),
        "ToState" -> optional(text),
        "SmsMessageSid" -> optional(text),
        "NumMedia" -> optional(number),
        "ToCity" -> optional(text),
        "FromZip" -> optional(text),
        "SmsSid" -> optional(text),
        "FromState" -> optional(text),
        "SmsStatus" -> optional(text),
        "FromCity" -> optional(text),
        "Body" -> text,
        "FromCountry" -> optional(text),
        "To" -> nonEmptyText,
        "ToZip" -> optional(text),
        "NumSegments" -> optional(text),
        "MessageSid" -> optional(text),
        "AccountSid" -> optional(text),
        "From" -> nonEmptyText,
        "ApiVersion" -> optional(text)
      )(MsgFormInput.apply)(MsgFormInput.unapply)
    )
  }
  //Action for GET (just an information page for clueless users who don't get how the service works)
  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index(config.getString("twilio.from_number")))
  }

  //Action for POST (receive; process; reply)
  def process: Action[AnyContent] =  Action.async
  { implicit request =>
    processJsonPost()
  }

  private def processJsonPost[A]()(
    implicit request: Request[A]): Future[Result] = {
    def failure(badForm: Form[MsgFormInput]) = {
      Future.successful(BadRequest(badForm.errorsAsJson))
    }

    def success(input: MsgFormInput) = {
      handler.handleMsg(input).map { twResult =>
        Accepted(twResult).as("application/xml")
      }
    }
    form.bindFromRequest().fold(failure, success)
  }
}
