package controllers

import com.typesafe.config.{Config, ConfigFactory}
import services.MsgHandler
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Configuration

import scala.concurrent.Future
import play.api.{Configuration, Environment}
import MsgHandler._

class mgt extends MsgHandler{
  override def handleMsg(postInput: MsgFormInput)={
    Future.successful("blah")
  }
}

class HomeControllerSpec extends PlaySpec with GuiceOneAppPerSuite with Injecting {

  val pageTxt = "This is not the kind of application that you access through a browser."

  val mh = new mgt()

  "HomeController GET" should {

    "render the index page from a new instance of controller" in {
      val controller = new HomeController(stubControllerComponents(), mh)
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include (pageTxt)
    }

    "render the index page from the application" in {
      val controller = inject[HomeController]
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include (pageTxt)
    }

    "render the index page from the router" in {
      val request = FakeRequest(GET, "/")
      val home = route(app, request).get

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include (pageTxt)
    }
  }
}
