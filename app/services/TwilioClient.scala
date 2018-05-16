package services

import com.twilio.Twilio
import com.twilio.`type`.PhoneNumber
import com.twilio.rest.api.v2010.account.Message
import com.twilio.twiml.MessagingResponse
import com.twilio.twiml.messaging.Body
import com.typesafe.config.ConfigFactory
import play.Logger


object TwilioClient{
  val cfg = ConfigFactory.load()
  val ACCOUNT_SID = cfg.getString("twilio.account_sid")
  val AUTH_TOKEN = cfg.getString("twilio.auth_token")
  val FROM_NUMBER = cfg.getString("twilio.from_number")
  val errorXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Message><Body>Internal error, sorry!</Body></Message></Response>"

  Twilio.init(ACCOUNT_SID, AUTH_TOKEN)

  def twMsg(to_number: String, msg: String): Unit = {
    try {
      val message = Message.creator(
        new PhoneNumber(to_number),
        new PhoneNumber(FROM_NUMBER),
        msg
      ).create()
    }
    catch {
      case e:Exception =>Logger.error(s"Exception ${e.toString} in TwilioClient.twMsg")
    }
    Logger.info(s"Sent: $msg to $to_number")
  }

  def twimlResponse(r:String):String ={
    try{ //Message collides with com.twilio.rest.api.v2010.account.Message, so we reference it directly
      val smsR = new com.twilio.twiml.messaging.Message.Builder().body(new Body.Builder(r).build()).build()
      val twiml  = new MessagingResponse.Builder().message(smsR).build()
      twiml.toXml
    }
    catch{
      case e:Exception => Logger.error( s"Exception ${e.toString} while parsing $r TwilioClient.twimlResponse" )
      errorXML
    }
  }

}
