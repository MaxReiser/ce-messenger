package at.jku.ce.ue.service;

import akka.actor.*;
import akka.pattern.PatternsCS;
import at.jku.ce.ue.api.*;
import at.jku.ce.ue.data.Room;
import at.jku.ce.ue.helper.CEHelper;
import com.typesafe.config.ConfigFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CompletableFuture;


public class ChatServiceActor extends AbstractLoggingActor {

	private HashMap<Room, HashSet<Participant>> rooms = new HashMap<>();
	private CEHelper helper = new CEHelper(this.context().system(), ConfigFactory.load("application.conf"));

	private HashSet<Participant> participants;

	private Timer timer = new Timer();
	private Boolean response = false;

	@Override
	public void preStart() {
		rooms.put(new Room("room#1"), new HashSet<>());
		rooms.put(new Room("room#2"), new HashSet<>());
		rooms.put(new Room("room#3"), new HashSet<>());
		response = false;
		for(Room r:rooms.keySet()) log().info("Available Rooms: " + r.getName());
		ActorSelection registry = this.context().system().actorSelection(helper.getChatServiceRegistry());
		registry.tell(new RegisterChatService(), self());
		timer.schedule(getRegistryTask(), 10000);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(GetStatus.class, status ->{
			this.sender().tell(new Heartbeat(), self());
			this.cleanUpRooms();
		})
				.match(ChatServiceRegistered.class, register ->{
					log().info("ChatServiceActor: chat service registered");
					response = true;
				})
				.match(JoinRoom.class, join -> {
					if(join.getRoom() != null && join.getName() != null) {
						participants = null;
						participants = rooms.get(join.getRoom());
						if (participants == null) {
							this.getSender().tell(new ErrorOccurred(ErrorOccurred.Error.ROOM_NOT_AVAILABLE), this.getSelf());
						}
						else {
							boolean added = participants.add(new Participant(join.getName(), this.sender()));
							if(added) {
								this.sender().tell(new RoomJoined(), this.self());
								log().info("ChatServiceActor: " + join.getName() + " joined");

								for (Participant p : participants) {
									p.ref.tell(new NewMessageAvailable(join.getRoom(), join.getName(), join.getName() + " joined the conversation"), self());
								}
							}
							else{
								this.getSender().tell(new ErrorOccurred(ErrorOccurred.Error.ROOM_ALREADY_JOINED), this.getSelf());
							}
						}
					}
				})
				.match(GetAvailableRooms.class, getAvailableRooms -> {
					Set<Room> roomsSet = new HashSet<>();
					roomsSet.addAll(rooms.keySet());
					this.sender().tell(new AvailableRooms(helper.getActorPath(self()), roomsSet), self());
				})
				.match(LeaveRoom.class, leaveRoom -> {
					if(leaveRoom.getRoom() != null) {
						participants = null;
						participants = rooms.get(leaveRoom.getRoom());
						if (participants == null) {
							this.getSender().tell(new ErrorOccurred(ErrorOccurred.Error.ROOM_NOT_AVAILABLE), this.getSelf());
						} else {
							boolean isRoomParticipant = false;
							for (Participant p : participants) {
								if (p.getRef().equals(this.getSender())) {
									participants.remove(p);
									this.sender().tell(new RoomLeft(leaveRoom.getRoom()), this.self());
									isRoomParticipant = true;
								}
							}
							if (!isRoomParticipant) {
								this.getSender().tell(new ErrorOccurred(ErrorOccurred.Error.ROOM_NOT_JOINED), this.getSelf());
							}
						}
					}
				})

				.match(SendMessage.class, msg -> {
					if(msg.getMessage() != null && msg.getRoom() != null) {
						participants = null;
						participants = rooms.get(msg.getRoom());
						if (participants == null) {
							this.getSender().tell(new ErrorOccurred(ErrorOccurred.Error.ROOM_NOT_AVAILABLE), this.getSelf());
						} else {
							boolean isRoomParticipant = false;
							for (Participant p : participants) {
								if (p.getRef().equals(this.getSender())) {
									isRoomParticipant = true;
									break;
								}
							}
							if (isRoomParticipant) {
								for (Participant p : participants) {
									p.ref.tell(new NewMessageAvailable(msg.getRoom(), p.name, msg.getMessage()), self());
								}
							} else {
								this.getSender().tell(new ErrorOccurred(ErrorOccurred.Error.ROOM_NOT_JOINED), this.getSelf());
							}
						}
					}
				})

				.build();
	}

	private class Participant implements Serializable, Comparable<Participant>{
		private String name;
		private ActorRef ref;

		public Participant(String name, ActorRef ref) {
			this.name = name;
			this.ref = ref;
		}

		public String getName() {
			return name;
		}

		public ActorRef getRef() {
			return ref;
		}

		@Override
		public int compareTo(Participant o) {
			return this.ref.compareTo(o.ref);
		}
	}

	public TimerTask getRegistryTask(){
		return new TimerTask() {
			@Override
			public void run() {
				if(!response) {
					log().error("ChatServiceActor ERROR: ChatService not registered");
				}
				response = false;
			}
		};
	}
	public void cleanUpRooms() {
		//todo increase timeout
		Iterator it = rooms.entrySet().iterator();
			while(it.hasNext()){
				Map.Entry pair = (Map.Entry)it.next();
				for(Participant p:(HashSet<Participant>)pair.getValue()){
					CompletableFuture<Object> future = PatternsCS.ask(p.ref, new GetStatus(), 15000).toCompletableFuture();
					future.thenApply(s -> {
						return s;
					}).exceptionally(err -> {
						log().info(p.toString() + " was removed from room " + pair.getKey().toString());
						((HashSet<Participant>)pair.getValue()).remove(p);
							return err;
					});
				}
			}
	}

}
