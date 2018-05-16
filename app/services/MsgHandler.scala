package services

import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

import com.oblac.nomen.Nomen
import javax.inject.Inject
import akka.actor.ActorSystem
import akka.actor.FSM.Failure
import redis.RedisClient
import com.typesafe.config.{Config, ConfigFactory}
import controllers.MsgFormInput
import play.Logger

import scala.util.Success


object MsgHandler {
  implicit val akkaSystem:ActorSystem = akka.actor.ActorSystem()
   implicit val cfg = ConfigFactory.load()

  private val rurl = cfg.getString("redis.url")
  private val rpat = """^redis://.*:(.*)@(.*):(.*)$""".r
  private val rparm = rurl match{
    case rpat(pwd, host, port) => (host,port,pwd)
    case _ => ("","0","")
  }
  val redis =  RedisClient(rparm._1, rparm._2.toInt, Some(rparm._3))
}

class MsgHandler @Inject()(implicit ec: ExecutionContext, implicit val cfg:Config) {

  private val randomGen = scala.util.Random
  private val nameGen = Nomen.est.adjective.pokemon.count
  val sendersStr = " your list of approved senders."
  val unchangedStr = " has not changed in relation to"
  val removedStr = " has been removed from"
  val addedStr = " has been added to"
  val welcomeStr = "Welcome to atxt!  Your ID is: "
  val intErrorStr = "Internal error. Please, try again!"

  case class anonMsg(name: Option[String], msg: Option[String])

  private def parseBody(body: String): anonMsg = {
    if (!body.isEmpty) {
      val l = body.split(" ")
      l match {
        case Array() => anonMsg(None, None)
        case Array(a) => anonMsg(Some(a), None)
        case _ => anonMsg(Some(l.head), Some(l.tail.mkString(" ")))
      }
    }
    else
      anonMsg(None, None)
  }

  private def toggleContact(sub: String, ch: String): Future[Boolean] = {
    val foo = for ( authorized <- MsgHandler.redis.sismember(sub, ch) ) yield
      if (authorized) for(nr<-MsgHandler.redis.srem(sub, ch)) yield false //removed
      else for(nr<-MsgHandler.redis.sadd(sub, ch)) yield true //added
    foo.flatten
  }

  def delSubscriber(key:String): Future[Long] = { //not used, but may come in handy, esp. in tests

    for (Some(nameb) <- MsgHandler.redis.get(key)) yield {
      val key2d = nameb.utf8String
      for{
        Some(aclb) <- MsgHandler.redis.hget(key2d, "acl")
        Some(numb) <- MsgHandler.redis.hget(key2d, "number")
      } yield{
        val acl2d = aclb.utf8String
        val n2d = numb.utf8String
        MsgHandler.redis.del(acl2d, n2d, key2d )
      }
    }.flatten
  }.flatten

  private def newSubscriber(key:String) = {

    val rndName = nameGen.withCount(randomGen.nextInt(9999)).get(); //e.g. clever_pikachu331
    val aclid = UUID.randomUUID().toString //unique key for ACL set
    for{
      nameTaken <- MsgHandler.redis.exists(rndName)
      aclidTaken <- MsgHandler.redis.exists(aclid)
    } yield{
      if( !(nameTaken | aclidTaken) ) { //as the subscriber count approaches 490 million, the probability of a name being taken rises. 😀
        for {
          ks <- MsgHandler.redis.set(key, rndName)
          ac <- MsgHandler.redis.hmset(rndName, Map("number" -> key, "acl" -> aclid))
          sa <- MsgHandler.redis.sadd(aclid, rndName) //authorize messaging self
        } yield {
          TwilioClient.twimlResponse(
            if (ks & ac & sa > 0) {
              Logger.info(s"Added new subscriber $rndName $key")
              welcomeStr + rndName
            }
            else {
              Logger.error(s"Adding new subscriber $rndName failed.  ks: $ks, ac: $ac, sa: $sa")
              intErrorStr
            }
          )
        }
      }
      else{
        Logger.error(s"Adding new subscriber failed.  $rndName taken: $nameTaken $aclid taken: $aclidTaken")
        Future.successful(TwilioClient.twimlResponse(intErrorStr))
      }
    }
  }.flatten  //flatten the nested Futures

   def handleMsg(postInput: MsgFormInput): Future[String] = {
     val key = postInput.From
     val body = parseBody(postInput.Body)
     val to = postInput.To
     try {
       MsgHandler.redis.exists(key).map(ex => {
         //strip the Future layer
         if (ex) {
           for (Some(nb) <- MsgHandler.redis.get(key)) yield { //retrieve sender's rndName
             val senderName = nb.utf8String
             for (Some(ab) <- MsgHandler.redis.hget(senderName, "acl")) yield {
               //retrieve sender's acl
               val senderACL = ab.utf8String
               body match {
                 case anonMsg(Some(contact), None) => //contact maintenance
                   for (ct <- toggleContact(senderACL, contact)) yield {
                     Logger.info((if (ct) "Enabling " else "Disabling ") + s" messages from $contact to $senderName")
                     TwilioClient.twimlResponse(contact + (if (ct) addedStr else removedStr) + sendersStr)
                   }
                 case anonMsg(Some(rcpt), Some(msg)) => //msg forwarding
                   Logger.info(s"Message $msg requested from: $senderName to: $rcpt ")
                   for {//could get both fields with hmget, but that adds a sequence and makes things harder
                     Some(rnb) <- MsgHandler.redis.hget(rcpt, "number")
                     Some(raclb) <- MsgHandler.redis.hget(rcpt, "acl")
                   } yield {
                     val rn = rnb.utf8String
                     val racl = raclb.utf8String
                     for (ok2Send <- MsgHandler.redis.sismember(racl, senderName)) yield {
                       if (ok2Send)
                         TwilioClient.twMsg(rn, s"From $senderName: " + msg) //Core function: relay msg through Twilio
                       else
                         Logger.warn(s"Unauthorized sender $senderName attempted to msg $rn")
                     }
                   }
                   Future.successful(TwilioClient.twimlResponse(s"Your message will be forwarded if $rcpt has added you ($senderName) to the list of approved senders."))
                 case _ =>
                   Logger.error(s"Failed to parse body ${body.toString}")
                   Future.successful(TwilioClient.twimlResponse(intErrorStr))
               }
             }
           }
         }.flatten.flatten //deal with the nested futures
         else {
           newSubscriber(key)
         }
       }).flatten //each return of Future adds another layer...  Flatten them to a single Future!
     }
    catch{
      case e:Exception => {
        Logger.error(s"Exception ${e.toString} in TwilioClient.twMsg")
        Future.successful(TwilioClient.twimlResponse(intErrorStr))
      }
    }
  }
}

