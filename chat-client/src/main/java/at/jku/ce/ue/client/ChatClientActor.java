package at.jku.ce.ue.client;

import akka.actor.*;
import akka.util.Timeout;
import at.jku.ce.ue.api.*;
import at.jku.ce.ue.data.Room;
import at.jku.ce.ue.helper.CEHelper;
import com.typesafe.config.ConfigFactory;
import scala.concurrent.duration.Duration;

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
    ActorRef inputActor = null;
    ActorSelection registry = null;
    int errorCounter = 0;
    int status = 0;
    String[] services;
    Room[] rooms;


    @Override
    public Receive createReceive() {
        return receiveBuilder().match(Start.class, startMessage ->{
            errorCounter = 0;
            status = 0;
            registry = this.context().system().actorSelection(helper.getChatServiceRegistry());
            this.requestServices();
            inputActor = this.context().system().actorOf(Props.create(ConsoleInputActor.class));
        })

        .match(AvailableRooms.class, availableRooms -> {
            this.receiveRooms(availableRooms);
        })

        .match(ChatRoomError.class, error -> {
            this.getContext().system().stop(this.getSender());
            roomActor = null;
            requestRooms();
        })

        .match(Status.Failure.class, err ->{
            errorCounter++;
            if(errorCounter<5) {
                printRed("ERROR: Not responding");
                requestServices();
            }
            else{
                printRed("ERROR: System terminated due to registry errors");
                System.in.close();
                context().system().stop(inputActor);
                context().system().stop(this.getSelf());
                context().system().terminate();
            }
        })

        .match(ChatRoomLeft.class, left->{
            requestRooms();
            roomActor = null;
        })

        .match(AvailableChatServices.class, services ->{
            errorCounter = 0;
            this.receiveChatServices(services);
        })

        .match(UserInput.class, input ->{
            if(roomActor != null){
                roomActor.tell(new ChatRoomActor.UserInput(input.getText()), self());
                inputActor.tell(new ConsoleInputActor.Read(), this.getSelf());
            }
            else{
                switch(status){
                    case 0:
                        receiveChoosenService(input.getText());
                        break;
                    case 1:
                        receiveChoosenRoom(input.getText());
                        break;

                }
            }
        }).build();
    }

    public void  receiveChatServices(AvailableChatServices message) {
        Set<String> temp = message.getChatServices();
        services = (temp).toArray(new String[temp.size()]);

        int i = 0;

        for (String service : services) {
            printWhite("ChatService[" + i++ + "]:\t" + service);
        }
        printWhite("Which Service would you like to choose?");

        printWhite("Command: ");
        inputActor.tell(new ConsoleInputActor.Read(), this.getSelf());
    }
    public void receiveChoosenService(String input){
        int selectedService = -1;
        boolean validInput = false;
        try {
                if(input.equals( "end")){
                    System.in.close();
                    this.context().system().stop(inputActor);
                    this.context().system().stop(this.getSelf());
                    context().system().terminate();
                    printWhite( "Chat Client shut down");
                    return;
                }

                try {
                    selectedService = Integer.parseInt(input);
                    if (selectedService >= 0 && selectedService < services.length) validInput = true;
                } catch (NumberFormatException ex) {
                    printRed("ERROR: Not a correct number");
                    return;
                }

            if(validInput) {
                this.chatService = services[selectedService];
                requestRooms();
            }
            inputActor.tell(new ConsoleInputActor.Read(), this.getSelf());
        }
        catch (Exception e){
                printWhite("Receive Services " + e.getMessage());
        }
    }

    public void receiveRooms(AvailableRooms message) {
        Set<Room> temp = message.getRooms();
        rooms = temp.toArray(new Room[temp.size()]);
        int i = 0;
        for (Room room : rooms) {
            printWhite("Room[" + i++ + "]:\t" + room.toString());
        }
        printWhite("Choose a room or go back to services");

        printWhite("Command: ");
    }
    public void receiveChoosenRoom(String input){
        boolean validInput = false;
        int selectedRoom = -1;
        try{
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
                        return;
                    case "join":
                        try {
                            if(param != null) {
                                if(name != null && name != "") {
                                    selectedRoom = Integer.parseInt(param);
                                    if (selectedRoom >= 0 && selectedRoom < rooms.length) validInput = true;
                                    else throw new NumberFormatException();
                                }
                                else{
                                    printRed("ERROR: No name given");
                                }
                            }
                            else{
                                printRed( "ERROR: No room number given");
                            }
                        } catch (NumberFormatException ex) {
                          printRed("ERROR: Not a correct room number");
                        }
                        if(validInput){
                            Room chatRoom = rooms[selectedRoom];

                            roomActor = this.context().system().actorOf(Props.create(ChatRoomActor.class));

                            roomActor.tell(new ChatRoomActor.JoinChatRoom(chatRoom, name, this.chatService), this.getSelf());
                        }
                        break;
                    default:
                        printRed("ERROR: Invalid command!");
                        break;
                }

            inputActor.tell(new ConsoleInputActor.Read(), this.getSelf());
        }
        catch (Exception e){
            printWhite(e.getMessage());
        }
    }

    private void requestRooms(){
        status = 1;
        ActorSelection chatService = this.context().system().actorSelection(this.chatService);
        CompletableFuture<Object> future = ask(chatService, new GetAvailableRooms(), t).toCompletableFuture();
        pipe(future, this.context().system().dispatcher()).to(this.getSelf());
    }

    private void requestServices(){
        status = 0;
        CompletableFuture<Object> future = ask(registry, new GetAvailableChatServices(), 5000).toCompletableFuture();
        pipe(future, this.context().system().dispatcher()).to(this.getSelf());
    }

    //Messages
    public static class Start{}
    public static class ChatRoomLeft{}
    public static class ChatRoomError{}
    public static class UserInput {
        String text = null;

        public String getText() {
            return text;
        }

        public UserInput(String text) {
            this.text = text;
        }
    }

    private void printWhite(String s) {
        System.out.println("\u001B[37m" + s);
    }

    private void printRed(String s) {
        System.out.println("\u001B[31m" + s);
        System.out.print("\u001B[37m" + "");
    }

}
