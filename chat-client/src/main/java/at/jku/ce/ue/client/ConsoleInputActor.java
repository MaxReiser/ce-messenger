package at.jku.ce.ue.client;

import akka.actor.AbstractActor;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ConsoleInputActor extends AbstractActor {
    @Override
    public Receive createReceive() {
        return receiveBuilder().match(Read.class, start ->{
            BufferedReader br = null;
            try {
                System.out.println("reading from keyboard...");
                br = new BufferedReader(new InputStreamReader(System.in));
                String input = br.readLine();
                this.getSender().tell(new ChatRoomActor.UserInput(input), this.getSelf());
            }
            catch(Exception e){
                System.out.println(e.getMessage());
            }
        }).build();
    }

    public static class Read{}
}
