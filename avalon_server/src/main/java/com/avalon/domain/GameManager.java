package com.avalon.domain;

import com.avalon.proto.base.Auth;
import com.avalon.proto.game.GameStatusUpdate;
import com.avalon.proto.game.Proposal;
import com.avalon.proto.game.VoteForPlayerRequest;
import com.avalon.util.ResponseException;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

public class GameManager {
  private final UserManager userManager;

  @GuardedBy("this")
  private final Map<Integer, GameInfo> gameInfoMap = new HashMap<>();

  private final AtomicInteger atomicInteger = new AtomicInteger();

  @Inject
  public GameManager(UserManager userManager) {
    this.userManager = userManager;
  }

  public synchronized GameInfo createGame(RoomManager.RoomInfo roomInfo) {
    int gameId = atomicInteger.incrementAndGet();
    GameInfo gameInfo = new GameInfo(gameId, roomInfo);
    gameInfoMap.put(gameId, gameInfo);

    return gameInfo;
  }

  public void pollGameStatus(
      Auth auth, int gameId, StreamObserver<GameStatusUpdate> responseObserver)
      throws ResponseException {
    userManager.validate(auth);
    GameInfo gameInfo = getGameInfo(gameId);
    gameInfo.avalonGame.gameStatusUpdateList.forEach(responseObserver::onNext);
    gameInfo.avalonGame.messageDispatcher.add(responseObserver);
  }

  public void propose(Auth auth, int gameId, Proposal proposal) throws ResponseException {
    userManager.validate(auth);
    GameInfo gameInfo = getGameInfo(gameId);

    gameInfo.avalonGame.propose(proposal);
  }

  public void voteForPlayer(Auth auth, int gameId, VoteForPlayerRequest.Voting voting)
      throws ResponseException {
    UserManager.UserInfo userInfo = userManager.validate(auth);
    GameInfo gameInfo = getGameInfo(gameId);
    int who = gameInfo.roomInfo.seats.get(userInfo.user);

    gameInfo.avalonGame.voteForPlayer(who, voting);
  }

  public boolean ladyTest(Auth auth, int gameId, int whom) throws ResponseException {
    UserManager.UserInfo userInfo = userManager.validate(auth);
    GameInfo gameInfo = getGameInfo(gameId);
    int who = gameInfo.roomInfo.seats.get(userInfo.user);

    return gameInfo.avalonGame.ladyTest(who, whom);
  }

  public void assassinate(Auth auth, int gameId, int whom) throws ResponseException {
    UserManager.UserInfo userInfo = userManager.validate(auth);
    GameInfo gameInfo = getGameInfo(gameId);
    int who = gameInfo.roomInfo.seats.get(userInfo.user);

    gameInfo.avalonGame.assassinate(who, whom);
  }

  public void voteFoTask(Auth auth, int gameId, boolean success) throws ResponseException {
    UserManager.UserInfo userInfo = userManager.validate(auth);
    GameInfo gameInfo = getGameInfo(gameId);
    int who = gameInfo.roomInfo.seats.get(userInfo.user);

    gameInfo.avalonGame.voteForTask(who, success);
  }

  private GameInfo getGameInfo(int gameId) throws ResponseException {
    GameInfo gameInfo = gameInfoMap.get(gameId);
    if (gameInfo == null) {
      throw new ResponseException("No such game");
    }

    return gameInfo;
  }

  static class GameInfo {
    final int gameId;
    final RoomManager.RoomInfo roomInfo;
    final AvalonGame avalonGame;

    private GameInfo(int gameId, RoomManager.RoomInfo roomInfo) {
      this.gameId = gameId;
      this.roomInfo = roomInfo;

      avalonGame = new AvalonGame(roomInfo.room);
    }
  }
}
