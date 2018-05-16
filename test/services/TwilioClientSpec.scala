package services
import org.scalatest.{FlatSpec, FunSuite}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.{GuiceOneAppPerSuite, GuiceOneAppPerTest}
import play.api.test.Injecting

class TwilioClientSpec extends PlaySpec with GuiceOneAppPerSuite with Injecting {
  "TwilioClient " must {
    "be configured with test security tokens and numbers" in {
      assert(TwilioClient.FROM_NUMBER == "+15005550006")
      assert(!TwilioClient.ACCOUNT_SID.isEmpty)
      assert(!TwilioClient.AUTH_TOKEN.isEmpty)
    }
  }

  val testXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Message><Body>teststring</Body></Message></Response>"

  "TwilioClient.twimlResponse " should {
    "form a correct XML response" in{
      assert( TwilioClient.twimlResponse("teststring") ==  testXML )
    }
  }

}
