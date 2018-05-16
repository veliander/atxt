package services

import org.scalatest.{FlatSpec, FunSuite}
import org.scalatestplus.play._
import play.api.test.Injecting

import scala.concurrent.ExecutionContext.Implicits.global
import MsgHandler._
import controllers.MsgFormInput
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerTest

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class MsgHandlerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting with ScalaFutures {

  val mh = new MsgHandler

  def genInput(from:String, to:String, body:String):MsgFormInput = {
    new MsgFormInput(
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      body,
      None,
      to,
      None,
      None,
      None,
      None,
      from,
      None)
  }

  val twnum = TwilioClient.FROM_NUMBER
  val num1 = "+14085551213"  //test phone numbers are tricky:  have to be valid from Twilio's point of view
  val num2 = "+14085551214" // but invalid from PSTN's
  val num3 = "+14085551215" // but invalid from PSTN's
  val num4 = "+14085551216" // but invalid from PSTN's

  val tstKey = "tstKey"
  val tstVal = "tstVal"

  "Redis" must {
    "be initialized and functional" in {
      whenReady(MsgHandler.redis.set(tstKey, tstVal)){
        res => {
          assert(res)
          Await.result(MsgHandler.redis.del(tstKey), 500 millis)
        }
      }
    }
  }

  val headStr = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Message><Body>"
  val tailStr = "</Body></Message></Response>"
  val newHeadStr = headStr+"Welcome to atxt!  Your ID is: "
  val authTailStr = "to your list of approved senders."+tailStr
  val msgHeadStr = headStr+"Your message will be forwarded if "
  val msgTailStr =  "to the list of approved senders."+tailStr
  var id1:String = ""
  var id2:String = ""
  var id3:String = ""
  var id4:String = ""
  private val createPat = """^.*\: (.+)</Body></Message></Response>$""".r
  private val authPat = """^.*<Body>(.+) has.*your list of approved senders.</Body></Message></Response>$""".r

  def createAccount(n:String)= {
    val response = mh.handleMsg(genInput(n, twnum, "Hello, atxt!"))
    for(str<-response) yield {
      assert(str.startsWith(headStr))
      assert(str.endsWith(tailStr))
      val id = str match {
        case createPat(alias) => alias
        case _ => "failed"
      }
      assert(id != "failed")
      id
    }
  }

  def auth(from:String, to:String) = {
    whenReady(mh.handleMsg(genInput(to, twnum, from))) { //the alias (from) goes in the body
      res => {
        assert(res.startsWith(headStr))
        assert(res.endsWith(authTailStr))
        val id = res match {
          case authPat(alias) => alias
          case _ => "failed"
        }
        assert(id == from)
      }
    }
  }

  def msg(from:String, to:String, body:String) = {
    whenReady(mh.handleMsg(genInput(from, twnum, to + " " + body))) {
      res => {
        assert(res.startsWith(msgHeadStr))
        assert(res.endsWith(msgTailStr))
      }
    }
  }

  "MsgHandler" should {

    "Create new accounts on First Contact" in {
      whenReady(createAccount(num1)) {
        res => {
          mh.delSubscriber(num1)
          id1 = res
        }
      }
    }

    "Authorize senders when requested" in {
      whenReady(createAccount(num2)) { res => id2 = res }
      auth("random_alias", num2)
      mh.delSubscriber(num2)
    }

    "Forward messages from authorized senders" in {

      whenReady(createAccount(num3)) { res => id3 = res }
      whenReady(createAccount(num4)) { res =>  id4 = res }
      auth(id3, num4)

      msg(num3, id4, "test message")
      Thread.sleep(2000) //give some time to asynch Twilio call to complete
      Await.result(mh.delSubscriber(num3), 500 millis)
      Await.result(mh.delSubscriber(num4), 500 millis)
    }

  }
}
