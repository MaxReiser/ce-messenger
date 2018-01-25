package at.jku.ce.ue.service;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import at.jku.ce.ue.helper.CEHelper;
import com.typesafe.config.ConfigFactory;

public class ChatService {

	public static void main(String[] args) {
		ActorSystem chatServiceSystem = ActorSystem.create("ChatService_52");
		CEHelper helper = new CEHelper(chatServiceSystem, ConfigFactory.load("application.conf"));
		ActorRef sampleActor = chatServiceSystem.actorOf(Props.create(ChatServiceActor.class), "chat-service-actor-53");//sample-actor
	}

}
