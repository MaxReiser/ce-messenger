package at.jku.ce.ue.client;

import akka.actor.*;
import at.jku.ce.ue.api.*;
import at.jku.ce.ue.data.Room;

import java.util.Timer;
import java.util.TimerTask;

public class ChatRoomActor extends AbstractActor {
    boolean active = false;
    private Room chatRoom;
    private ActorRef trigger;
    private ActorSelection chatService;
    private ActorRef inputActor;
    private String name;
    boolean response = false;
    private Timer timer = new Timer();
    int leaveCounter = 0;
    long lastReceived = 0;
    int spamCounter = 0;

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(JoinChatRoom.class, start ->{
            active = false;
            trigger = this.getSender();
            this.chatRoom = start.getRoom();
            this.name = start.getName();
            chatService = this.context().system().actorSelection(start.getChatService());
            chatService.tell(new JoinRoom(this.chatRoom, this.name), this.getSelf());
            timer.schedule(getChatRoomTask(), 5000);
        })

        .match(RoomJoined.class, rooms ->{
            active = response = true;
            printGreen("Room successfully joined");
            inputActor = this.context().system().actorOf(Props.create(ConsoleInputActor.class));
            inputActor.tell(new ConsoleInputActor.Read(), this.getSelf());
        })

        .match(ErrorOccurred.class, error ->{
            response = true;
            active = false;
            switch(error.getError()){
                case ROOM_NOT_JOINED:
                    trigger.tell(new ChatClientActor.ChatRoomError(), this.getSelf());
                    this.getContext().getSystem().stop(inputActor);
                    printRed("ERROR: Room not joined");
                    break;
                case ROOM_NOT_AVAILABLE:
                    trigger.tell(new ChatClientActor.ChatRoomError(), this.getSelf());
                    this.getContext().getSystem().stop(inputActor);
                    printRed("ERROR: This room is not available, choose another room");
                    break;
                case ROOM_ALREADY_JOINED:
                    printRed("ERROR: Room already joined");
                    break;
            }
        })

        .match(NewMessageAvailable.class, message ->{
            if(active) {
                long receiveTime = System.currentTimeMillis();
                response = true;

                if ((receiveTime - lastReceived) > 200) {
                    spamCounter++;
                }
                lastReceived = receiveTime;

                if (spamCounter < 6) {
                    String output = "";
                    if (message.getName() != null) output += message.getName();
                    else output += "Unknown";

                    output += " in ";

                    if (message.getRoom() == null || message.getRoom().getName() == null) output += "unknown Room";
                    else output += message.getRoom().getName();

                    output += ": ";

                    if (message.getMessage() == null) output += "empty Message";
                    else output += message.getMessage();

                    printGreen(output);

                } else {
                    printRed("Spamming recognized. Leaving room...");
                    inputActor.tell(new ConsoleInputActor.Read(), this.getSelf());
                    leaveRoom();
                }
            }
        })

        .match(RoomLeft.class, left -> {
            response = true;
            active = false;
            timer.cancel();
            printGreen(left.getRoom() + " successfully left");
            this.context().system().stop(this.inputActor);
            this.trigger.tell(new ChatClientActor.ChatRoomLeft(), this.getSelf());
            this.context().system().stop(this.getSelf());
        })

        .match(GetStatus.class, req -> {
            this.getSender().tell(new Heartbeat(), this.getSelf());
        })

        .match(UserInput.class, input ->{
            if(active){
                this.chat(input);
                this.getSender().tell(new ConsoleInputActor.Read(), this.getSelf());
            }
        }).build();
    }

    public void chat(UserInput input){
        String[] parts = input.getText().split("\\s+");

        switch(parts[0]){
            case "leave":
                response = false;
                leaveRoom();
                timer.schedule(getLeaveRoomTask(), 5000);
                break;
            case "send":
                response = false;
                chatService.tell(new SendMessage(this.chatRoom, input.getText().substring(parts[0].length()+1, input.getText().length())), this.getSelf());
                timer.schedule(getChatRoomTask(), 5000);
                break;
            default:
                printRed("Wrong command: use \"send\" to send a message or \"leave\" to leave the room");
                inputActor.tell(new ConsoleInputActor.Read(), self());
                break;
        }
    }

    private void leaveRoom(){
        leaveCounter = 0;
        chatService.tell(new LeaveRoom(this.chatRoom), this.getSelf());
        timer.schedule(getLeaveRoomTask(), 5000);
        active = false;
    }


    public static class JoinChatRoom{
        String name = null;
        Room chatRoom = null;
        String chatService = null;

        public JoinChatRoom(Room chatRoom, String name, String chatService){
            this.chatRoom = chatRoom;
            this.name = name;
            this.chatService = chatService;
        }

        String getName(){
            return this.name;
        }

        Room getRoom(){
            return chatRoom;
        }

        String getChatService(){
            return this.chatService;
        }
    }
    public static class UserInput{
        String text = null;

        public String getText() {
            return text;
        }

        public UserInput(String text){
            this.text = text;
        }
    }

    public TimerTask getChatRoomTask(){
        return new TimerTask() {
            @Override
            public void run() {
                if(!response && active) {
                    printRed("ERROR: Chat service not responding");
                    if (inputActor != null) {
                        getContext().getSystem().stop(inputActor);
                    }
                    trigger.tell(new ChatClientActor.ChatRoomError(), getSelf());
                }
            }
        };
    }

    public TimerTask getLeaveRoomTask(){
        return new TimerTask() {
            @Override
            public void run() {
                if(!response) {
                    leaveCounter++;
                    if(leaveCounter > 5) {
                        printRed("ERROR: Chat room not responding");
                        if(inputActor != null){
                            getContext().getSystem().stop(inputActor);
                        }
                        trigger.tell(new ChatClientActor.ChatRoomError(), getSelf());
                        timer.cancel();
                    }
                    else{
                        chatService.tell(new LeaveRoom(chatRoom), getSelf());
                        timer.schedule(getLeaveRoomTask(), 5000);
                        active = false;
                    }
                }
            }
        };
    }
    private void printGreen(String s) {
        System.out.println("\u001B[32m" + s);
    }

    private void printRed(String s) {
        System.out.println("\u001B[31m" + s);
    }
}
