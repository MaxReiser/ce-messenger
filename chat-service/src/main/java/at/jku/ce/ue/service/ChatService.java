package at.jku.ce.ue.service;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import at.jku.ce.ue.helper.CEHelper;
import com.typesafe.config.ConfigFactory;

import java.util.Random;

public class ChatService {

	public static void main(String[] args) {

		ActorSystem chatServiceSystem = ActorSystem.create("test2");
		CEHelper helper = new CEHelper(chatServiceSystem, ConfigFactory.load("application.conf"));

		// ChatServiceActor
		ActorRef sampleActor = chatServiceSystem.actorOf(Props.create(ChatServiceActor.class), "chat-service-actor-53");//sample-actor

		// get fully qualified path of sample actor
		System.out.println(helper.getActorPath(sampleActor));

		//sampleActor.tell(new ChatServiceActor.Start("room#1", "room#2", "room#3"), ActorRef.noSender());
	}

}
