
package com.memoriadeferrita

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source, Tcp}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.util.ByteString
import akka.serialization._

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ServerBinary {
  def main(args: Array[String]): Unit = {
    val serverSystem = ActorSystem("server")
    val clientSystem = ActorSystem("client")
    val (address, port) = ("127.0.0.1", 6000)
    server(serverSystem, address, port)
    client(clientSystem, address, port)
  }

  private def server(system: ActorSystem, address: String, port: Int): Unit = {
    implicit val sys = system
    import system.dispatcher
    implicit val materializer = ActorMaterializer()
    val handler = Sink.foreach[Tcp.IncomingConnection] { conn =>
      println("Client connected from: " + conn.remoteAddress)
      conn handleWith Flow.fromFunction[ByteString, ByteString] {
        a =>
          {
            println("Received=" + a.decodeString("UTF-8"))
            a
          }
      }
    }

    val connections = Tcp().bind(address, port)
    val binding = connections.to(handler).run()

    binding.onComplete {
      case Success(b) =>
        println("Server started, listening on: " + b.localAddress)
      case Failure(e) =>
        println(s"Server could not bind to $address:$port: ${e.getMessage}")
        system.terminate()
    }
  }
  private def client(system: ActorSystem, address: String, port: Int): Unit = {
    implicit val sys = system
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val testInput = ('a' to 'z').map(ByteString(_))

    val result = Source(testInput).via(Tcp().outgoingConnection(address, port)).
      runFold(ByteString.empty) { (acc, in) ⇒ acc ++ in }

    result.onComplete {
      case Success(result) =>
        println(s"Result: " + result.utf8String)
      case Failure(e) =>
        println("Failure: " + e.getMessage)
        system.terminate()
    }

  }

}
object ServerHttp {
  def main(args: Array[String]): Unit = {
    val serverSystem = ActorSystem("server")
    //val clientSystem = ActorSystem("client")
    val (address, port) = ("127.0.0.1", 6000)
    server(serverSystem, address, port)
  }

  private def server(system: ActorSystem, address: String, port: Int): Unit = {
    implicit val sys = system
    import system.dispatcher
    implicit val materializer = ActorMaterializer()
    val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
      Http().bind(address, port)

    val requestHandler: HttpRequest => HttpResponse = {
      case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
        HttpResponse(entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          "<html><body>Hello world!</body></html>"))
      case r: HttpRequest =>
        r.discardEntityBytes() // important to drain incoming HTTP Entity stream
        HttpResponse(404, entity = "Unknown resource!")
    }
    val bindingFuture: Future[Http.ServerBinding] = serverSource.to(Sink.foreach {
      connection =>
        println("Connection Accepted =>" + connection.remoteAddress)
        connection.handleWithSyncHandler(requestHandler);
    }).run()


  }
}