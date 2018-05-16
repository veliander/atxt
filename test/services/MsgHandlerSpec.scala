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

  private def genInput(from:String, to:String, body:String) = {
     MsgFormInput(
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

  "Redis" must {
    "be initialized and functional" in {
      val tstKey = "tstKey"
      val tstVal = "tstVal"
      whenReady(MsgHandler.redis.set(tstKey, tstVal)){
        res => {
          assert(res)
          Await.result(MsgHandler.redis.del(tstKey), timeout)
        }
      }
    }
  }

  private val headStr = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Message><Body>"
  private val tailStr = "</Body></Message></Response>"
  private val newHeadStr = headStr+"Welcome to atxt!  Your ID is: "
  private val authTailStr = "to your list of approved senders."+tailStr
  private val msgHeadStr = headStr+"Your message will be forwarded if "
  private val msgTailStr =  "to the list of approved senders."+tailStr
  private val createPat = """^.*\: (.+)</Body></Message></Response>$""".r
  private val authPat = """^.*<Body>(.+) has.*your list of approved senders.</Body></Message></Response>$""".r
  private val timeout = 1500 millis //1.5 sec is a lot, but with shorter periods tests sometimes fail on very slow machines
  private val twnum = TwilioClient.FROM_NUMBER
  private val mh = new MsgHandler

  private def createAccount(n:String)= {
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

  private def auth(from:String, to:String) = {
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

  private def msg(from:String, to:String, body:String) = {
    whenReady(mh.handleMsg(genInput(from, twnum, to + " " + body))) {
      res => {
        assert(res.startsWith(msgHeadStr))
        assert(res.endsWith(msgTailStr))
      }
    }
  }

  "MsgHandler" should {
    "Create new accounts on First Contact" in {
      val num = "+14085551213"  //test phone numbers are tricky:  have to be valid from Twilio's point of view
      whenReady(createAccount(num)) {
        res => {
          mh.delSubscriber(num)
        }
      }
    }

    "Authorize senders when requested" in {
      val num = "+14085551214"
      whenReady(createAccount(num)) { res => res }
      auth("random_alias", num)
      mh.delSubscriber(num)
    }

    "Forward messages from authorized senders" in {
      val num1 = "+14085551215"
      val num2 = "+14085551216"

      val id1 = whenReady(createAccount(num1)) { res => res }
      val id2 = whenReady(createAccount(num2)) { res =>  res }
      auth(id1, num2)

      msg(num1, id2, "test message")
      Thread.sleep(2000) //allow some time for asynch Twilio call to complete
      Await.result(mh.delSubscriber(num1), timeout)
      Await.result(mh.delSubscriber(num2), timeout)
    }
  }
}
