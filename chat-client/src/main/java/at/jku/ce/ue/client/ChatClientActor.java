package at.jku.ce.ue.client;

import akka.actor.*;
import akka.util.Timeout;
import at.jku.ce.ue.api.*;
import at.jku.ce.ue.data.Room;
import at.jku.ce.ue.helper.CEHelper;
import com.typesafe.config.ConfigFactory;
import scala.concurrent.duration.Duration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.Set;

import static akka.pattern.PatternsCS.ask;
import static akka.pattern.PatternsCS.pipe;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ChatClientActor extends AbstractLoggingActor {
    Timeout t = new Timeout(Duration.create(5, TimeUnit.SECONDS));
    CEHelper helper = new CEHelper(this.context().system(), ConfigFactory.load());
    String chatService = null;
    ActorRef roomActor = null;
    ActorSelection registry = null;
    int errorCounter = 0;

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(Start.class, startMessage ->{
            errorCounter = 0;
            registry = this.context().system().actorSelection(helper.getChatServiceRegistry());
            this.requestServices();
        })

        .match(AvailableRooms.class, availableRooms -> {
            this.receiveRooms(availableRooms);
        })

        .match(ChatRoomError.class, error -> {
            this.getContext().system().stop(this.getSender());
            requestRooms();
        })

        .match(Status.Failure.class, err ->{
            errorCounter++;
            if(errorCounter<5) {
                System.out.println("ERROR: Not responding");
                requestServices();
            }
            else{
                System.out.println("ERROR: System terminated due to registry errors");
                context().system().stop(this.getSelf());
                context().system().terminate();
            }
        }).match(ChatRoomLeft.class, left->{
            requestRooms();
        }).match(AvailableChatServices.class, services ->{
            errorCounter = 0;
            this.receiveChatServices(services);
        }).build();
    }

    public void receiveChatServices(AvailableChatServices message){
        BufferedReader br = null;
        boolean validInput = false;

        try{
            br = new BufferedReader(new InputStreamReader(System.in));
            Set<String> temp = message.getChatServices();
            String[] services = (temp).toArray(new String[temp.size()]);
            int selectedService = -1;
            int i = 0;

            for(String service : services){
                System.out.println("ChatService[" + i++ + "]:\t" + service);
            }
            System.out.println("Which Service would you like to choose?");

            do {
                System.out.println("Command: ");
                String input = br.readLine();

                if(input.equals("end")){
                    this.context().system().stop(this.getSelf());
                    context().system().terminate();
                    System.out.println("Chat Client shut down");
                    return;
                }

                try {
                    selectedService = Integer.parseInt(input);
                    if (selectedService >= 0 && selectedService < services.length) validInput = true;
                } catch (NumberFormatException ex) {
                    System.out.println("ERROR: Not a correct number");
                }

            }while(!validInput);

            this.chatService = services[selectedService];
            requestRooms();

        }
        catch (Exception e){
                System.out.println(e.getMessage());
        }
    }

    public void receiveRooms(AvailableRooms message){
       BufferedReader br = null;

        boolean validInput = false;
        int selectedRoom = -1;

        try{
            br = new BufferedReader(new InputStreamReader(System.in));

            Set<Room> temp = message.getRooms();
            Room[] rooms = temp.toArray(new Room[temp.size()]);
            int i = 0;
            for(Room room : rooms){
                System.out.println("Room[" + i++ + "]:\t" + room.toString());
            }
            System.out.println("Choose a room or go back to services");
            do {
                System.out.println("Command: ");
                String input = br.readLine();
                log().info("read: " + input);//todo
                String[] parts = input.split("\\s+");

                String signalWord = parts[0];
                String param = null;
                String name = null;
                if(parts.length > 1) {
                    param = parts[1];
                }
                if(parts.length > 2){
                    name = parts[2];
                }

                switch(signalWord){
                    case "services":
                        requestServices();
                        validInput = true;
                        break;
                    case "join":
                        try {
                            if(param != null) {
                                if(name != null && name != "") {
                                    selectedRoom = Integer.parseInt(param);
                                    if (selectedRoom >= 0 && selectedRoom < rooms.length) validInput = true;
                                    else throw new NumberFormatException();
                                }
                                else{
                                    log().info("ERROR: No name given");
                                }
                            }
                            else{
                                log().info("ERROR: No room number given");
                            }
                        } catch (NumberFormatException ex) {
                          log().info("ERROR: Not a correct room number");
                        }
                        if(validInput){
                            Room chatRoom = rooms[selectedRoom];

                            roomActor = this.context().system().actorOf(Props.create(ChatRoomActor.class), "room-actor");

                            roomActor.tell(new ChatRoomActor.JoinChatRoom(chatRoom, name, this.chatService), this.getSelf());
                        }
                        break;
                    default:
                        log().info("ERROR: Invalid command!");
                        break;
                }
            }while(!validInput);
        }
        catch (Exception e){
            System.out.println(e.getMessage());
        }
    }

    private void requestRooms(){
        ActorSelection chatService = this.context().system().actorSelection(this.chatService);
        CompletableFuture<Object> future = ask(chatService, new GetAvailableRooms(), t).toCompletableFuture();

        pipe(future, this.context().system().dispatcher()).to(this.getSelf());
    }

    private void requestServices(){
        CompletableFuture<Object> future = ask(registry, new GetAvailableChatServices(), 5000).toCompletableFuture();
        pipe(future, this.context().system().dispatcher()).to(this.getSelf());
    }

    //Messages
    public static class Start{}
    public static class ChatRoomLeft{}
    public static class ChatRoomError{}
}
