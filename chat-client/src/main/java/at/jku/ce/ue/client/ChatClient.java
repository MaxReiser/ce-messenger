package at.jku.ce.ue.client;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;


public class ChatClient {

    public static void main(String[] args) {
        ActorSystem chatClientSystem = ActorSystem.create("chat-client");
        final ActorRef chatClientActor = chatClientSystem.actorOf(Props.create(ChatClientActor.class),"chat-client-actor" );
        chatClientActor.tell(new ChatClientActor.Start(), ActorRef.noSender());
    }
}
