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
                br = new BufferedReader(new InputStreamReader(System.in));
                String input = br.readLine();
                this.getSender().tell(new ChatClientActor.UserInput(input), this.getSelf());
            }
            catch(Exception e){
                System.out.println(e.getMessage());
            }
        }).build();
    }

    private void printWhite(String s) {
        System.out.println("\u001B[37m" + s);
    }
    public static class Read{}
}
