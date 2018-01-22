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

    /*public static void receiveChatServices(Object o){
        try{

            Set<String> temp = (Set<String>) o;
            String[] services = (temp).toArray(new String[temp.size()]);
            int i = 0;
            for(String service : services){
                System.out.println("ChatService[" + i++ + "]:\t" + service);
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            int selectedService = Integer.parseInt(br.readLine());

        }
        catch (Exception e){
            System.out.println(e.getMessage());
        }
    }*/

    /*public static void receiveRooms(Object o){
        System.out.println("Main: Rooms received");
        try{

            Set<Room> temp = ((AvailableRooms)o).getRooms();
            Room[] rooms = temp.toArray(new Room[temp.size()]);
            int i = 0;
            for(Room room : rooms){
                System.out.println("Room[" + i++ + "]:\t" + room.toString());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            int selectedRoom = Integer.parseInt(br.readLine());
            System.out.println(selectedRoom);
        }
        catch (Exception e){
            System.out.println(e.getMessage());
        }
    }*/
}
