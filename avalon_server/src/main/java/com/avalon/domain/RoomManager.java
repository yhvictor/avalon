package com.avalon.domain;

import com.avalon.proto.base.Auth;
import com.avalon.proto.game.Room;
import com.avalon.proto.service.RoomUpdate;
import com.avalon.proto.service.User;
import com.avalon.util.ResponseException;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

public class RoomManager {
  private final UserManager userManager;
  private final GameManager gameManager;

  @GuardedBy("this")
  private final Map<String, RoomInfo> map = new TreeMap<>();

  @Inject
  RoomManager(UserManager userManager, GameManager gameManager) {
    this.userManager = userManager;
    this.gameManager = gameManager;
  }

  public synchronized Room createRoom(Auth auth, Room room) throws ResponseException {
    userManager.validate(auth);

    if (map.containsKey(room.getRoomName())) {
      throw new ResponseException("Name already token");
    }

    RoomInfo roomInfo = new RoomInfo(auth.getId(), room);
    map.put(room.getRoomName(), roomInfo);

    return room;
  }

  public synchronized List<Room> listRoom() {
    return map.values().stream().map(value -> value.room).collect(Collectors.toList());
  }

  public synchronized void joinRoom(Auth auth, String roomName, StreamObserver<RoomUpdate> observer)
      throws ResponseException {
    UserManager.UserInfo userInfo = userManager.validate(auth);
    RoomInfo roomInfo = getRoomInfo(roomName);

    for (Map.Entry<User, Integer> seat : roomInfo.seats.entrySet()) {
      RoomUpdate.UserJoinedUpdate userJoinedUpdate =
          RoomUpdate.UserJoinedUpdate.newBuilder()
              .setUser(seat.getKey())
              .setPosition(seat.getValue())
              .build();
      observer.onNext(RoomUpdate.newBuilder().setUserJoinedUpdate(userJoinedUpdate).build());
    }

    roomInfo.messageDispatcher.add(observer);
    roomInfo.assignSeat(userInfo.user, -1);
    if (roomInfo.gameInfo != null) {
      RoomUpdate.GameStartUpdate gameStartUpdate =
          RoomUpdate.GameStartUpdate.newBuilder().setGame(roomInfo.gameInfo.gameId).build();
      observer.onNext(RoomUpdate.newBuilder().setGameStartUpdate(gameStartUpdate).build());
    }
  }

  public synchronized void assignSeat(Auth auth, String roomName, int position)
      throws ResponseException {
    UserManager.UserInfo userInfo = userManager.validate(auth);
    RoomInfo roomInfo = getRoomInfo(roomName);

    roomInfo.assignSeat(userInfo.user, position);
  }

  public synchronized void startGame(Auth auth, String roomName) throws ResponseException {
    UserManager.UserInfo userInfo = userManager.validate(auth);
    RoomInfo roomInfo = getRoomInfo(roomName);
    if (roomInfo.owner != userInfo.auth.getId()) {
      throw new ResponseException("Only owner can start game");
    }
    if (roomInfo.gameInfo != null) {
      throw new ResponseException("Game already started");
    }

    roomInfo.gameInfo = gameManager.createGame(roomInfo);
    roomInfo.messageDispatcher.dispatch(
        RoomUpdate.newBuilder()
            .setGameStartUpdate(
                RoomUpdate.GameStartUpdate.newBuilder().setGame(roomInfo.gameInfo.gameId).build())
            .build());
  }

  private synchronized RoomInfo getRoomInfo(String roomName) throws ResponseException {
    RoomInfo roomInfo = map.get(roomName);
    if (roomInfo == null) {
      throw new ResponseException("No such room");
    }

    return roomInfo;
  }

  public static class RoomInfo {
    public final int owner;
    public final Room room;
    public final Map<User, Integer> seats = new HashMap<>();
    public final List<User> sitPlayers;
    public final MessageDispatcher<RoomUpdate> messageDispatcher = new MessageDispatcher<>();
    public GameManager.GameInfo gameInfo;

    private RoomInfo(int owner, Room room) {
      this.owner = owner;
      this.room = room;
      this.sitPlayers = new ArrayList<>(room.getCharacterCount() + 1);
    }

    public void assignSeat(User user, int position) throws ResponseException {
      if (position < -1 || position >= room.getCharacterCount()) {
        throw new ResponseException("Invalid position");
      }
      if (position != -1 && sitPlayers.get(position) != null) {
        throw new ResponseException("Position already token");
      }
      int currentPosition = seats.getOrDefault(user, -1);
      if (currentPosition > -1) {
        sitPlayers.set(currentPosition, null);
      }

      seats.put(user, position);
      sitPlayers.set(position, user);
      messageDispatcher.dispatch(
          RoomUpdate.newBuilder()
              .setUserJoinedUpdate(
                  RoomUpdate.UserJoinedUpdate.newBuilder().setUser(user).setPosition(position))
              .build());
    }
  }
}
