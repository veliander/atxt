# atxt (Anonymized TeXT)– a pseudonymous, non-blocking SMS forwarding service concept written in Scala 

atxt uses Twilio's SMS functionality to forward text messages without revealing phone numbers, or any other personally identifiable information.  The service is very lightweight, with no registration or account creation necessary.  Interaction with the system is simple and consists of 3 phases:

* **First contact**: whenever atxt receives an SMS (any text) from a new number, it assigns a unique alias to that number and adds it to its subscriber list.

    ![first contact](img/first-contact.png)

* **[De]Authorization**: whenever atxt receives from an existing subscriber an SMS consisting of a single word, it considers that word to be a subscriber alias and attempts to toggle the authorization of that alias to send messages to the sender (by default, one's own alias can send messages to itself, but no other alias is authorized to do so).

    ![authorization](img/authorization.png)

* **Forwarding**: whenever atxt receives from an existing subscriber an SMS consisting of two or more words, it considers the first word to be the recipient's alias, and all subsequent words to comprise a message.  If sender's alias is authorized to send messages to the recipient's alias, the message is thus forwarded to the phone number associated with the recipient's alias, with indication that it originates from sender's alias. 
    ![forward sent](img/fwd-sent.png) ![forward received](img/fwd-rcv.png)
## How to get running

After taking care of the prerequisites, you should get the service running locally and passing all tests.  After that, deploy to your platform of choice, point your Twilio number's Messaging Webhook to your service URL and select POST as the method.

### Prerequisites

* A Twilio development account ([free trial account](https://www.twilio.com/docs/usage/tutorials/how-to-use-your-free-trial-account) OK) with at least one phone number.
* Access to a Redis service (you can [roll your own](http://try.redis.io/), or use [Heroku's free instance] (https://devcenter.heroku.com/articles/heroku-redis)).

### Installing

Install sbt (although if you don't have it already, perhaps this is not the right project for you)

Set the following environment variables:

```
TWILIO_ACCOUNT_SID
TWILIO_AUTH_TOKEN
TWILIO_PHONE_NUMBER
REDIS_URL
PLAY_HTTP_SECRET_KEY
```
For proper values, see the [Twilio](https://www.twilio.com/docs/iam/test-credentials), Redis, and Play documentation. 

## Running the tests

Make sure the environment variables are set correctly:

* REDIS_URL contains proper credentials and points to a service that is running and accessible
* Twilio variables are configured with [test credentials](https://www.twilio.com/docs/iam/test-credentials)

When ready, type

```
sbt test
```
Make sure all tests pass before proceeding further.

## Deployment

You can deploy atxt on any platform, from your laptop (provided you allow Twilio to send POST requests to it), to one or more Heroku dynos, to a dedicated cluster on AWS.  Just keep in mind that the goal here is to provide a simple example of non-blocking code, not a secure communications platform. 😀

Don't forget to set the real credentials of your Twilio account in the ENV variables.

My reference deployment platform is [Heroku](https://www.heroku.com/) (a free dyno using a free Heroku Redis instance).  It works well, but if the dyno falls asleep, the message that awakes it may result in an error as Twilio times out in 15 seconds, while a sleeping Heroku dyno takes 30 seconds to wake up.

## Built With

* Scala 2.12 (works a lot better than previous versions for [flattening Futures](https://stackoverflow.com/questions/42492159/equivalent-of-flatten-method-in-scala-2-11-to-handle-nested-futures?rq=1)).
* [Play 2.6](https://www.playframework.com/) - The reactive web framework from Lightbend.
* [Rediscala](https://github.com/etaty/rediscala) - A Redis client for Scala (2.10+) and AKKA (2.2+) with non-blocking and asynchronous I/O operations.  Writen by @etaty.
* [Twilio Java SDK](https://www.twilio.com/docs/libraries/java) - Although Java-only, still the best way to call Twilio API functions from Scala.
* [nomen-est-omen](https://rometools.github.io/rome/) - A random, composite name generation library by @igr.

## Authors

* **@veliander** 

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details

