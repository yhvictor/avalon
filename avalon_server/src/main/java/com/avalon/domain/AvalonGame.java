package com.avalon.domain;

import com.avalon.proto.game.Character;
import com.avalon.proto.game.GameStatusUpdate;
import com.avalon.proto.game.Proposal;
import com.avalon.proto.game.Room;
import com.avalon.proto.game.VoteForPlayerRequest;
import com.avalon.util.ResponseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class AvalonGame {
  private final Room room;
  private final AtomicInteger index = new AtomicInteger();
  final MessageDispatcher<GameStatusUpdate> messageDispatcher = new MessageDispatcher<>();
  private final List<com.avalon.proto.game.Character> characters = new ArrayList<>();

  final List<GameStatusUpdate> gameStatusUpdateList = new ArrayList<>();
  private final List<VoteForPlayerRequest.Voting> currentVote;
  private final List<Boolean> taskVote;
  private int leader;
  private int ladyByTheLake;
  private int mission = 0;
  private int round = 0;
  private boolean allowProposal = true;
  private List<Integer> lastChoice;

  AvalonGame(Room room) {
    this.room = room;

    characters.addAll(room.getCharacterList());
    Collections.shuffle(characters);
    currentVote = new ArrayList<>(room.getCharacterCount());
    taskVote = new ArrayList<>(room.getCharacterCount());

    leader = new Random().nextInt(characters.size());
    ladyByTheLake = leader;
    newRound();
  }

  public void propose(Proposal proposal) throws ResponseException {
    if (!allowProposal) {
      throw new ResponseException("Currently not supported to propose");
    }

    if (proposal.getFromWhom() == leader) {
      lastChoice = proposal.getPlayersList();
    }

    GameStatusUpdate gameStatusUpdate =
        GameStatusUpdate.newBuilder()
            .setIndex(index.incrementAndGet())
            .setProposal(proposal)
            .build();
    dispatchAndLog(gameStatusUpdate);

    maybeTriggerRoundEnd();
  }

  public void voteForPlayer(int who, VoteForPlayerRequest.Voting voting) {
    currentVote.set(who, voting);

    GameStatusUpdate gameStatusUpdate =
        GameStatusUpdate.newBuilder()
            .setIndex(index.incrementAndGet())
            .setVotedForPlayer(
                GameStatusUpdate.VotedForPlayer.newBuilder()
                    .setFromWhom(who)
                    .setVoted(voting != VoteForPlayerRequest.Voting.UNKNOWN)
                    .build())
            .build();
    dispatchAndLog(gameStatusUpdate);
  }

  public void voteForTask(int who, boolean success) throws ResponseException {
    if (!lastChoice.contains(who)) {
      throw new ResponseException("Not in the task team");
    }

    taskVote.set(who, success);

    GameStatusUpdate gameStatusUpdate =
        GameStatusUpdate.newBuilder()
            .setIndex(index.incrementAndGet())
            .setVotedForMission(GameStatusUpdate.VotedForMission.newBuilder().setFromWhom(who).build())
            .build();
    dispatchAndLog(gameStatusUpdate);

    boolean allVoted = true;
    boolean isAllSuccess = true;
    for (Integer integer : lastChoice) {
      Boolean vote = taskVote.get(integer);
      if (vote == null) {
        allVoted = false;
      } else {
        isAllSuccess &= vote;
      }
    }

    if (!allVoted) {
      return;
    }

    triggerMissionResult(isAllSuccess);
  }

  public boolean ladyTest(int who, int whom) {
    ladyByTheLake = whom;

    GameStatusUpdate gameStatusUpdate =
        GameStatusUpdate.newBuilder()
            .setIndex(index.incrementAndGet())
            .setLadyTestDone(
                GameStatusUpdate.LadyTestDone.newBuilder().setFromWhom(who).setToWhom(whom).build())
            .build();
    dispatchAndLog(gameStatusUpdate);

    return isBlue(characters.get(whom));
  }

  public void assassinate(int who, int whom) {
    // TODO: impl later.
  }

  private void maybeTriggerRoundEnd() {
    if (lastChoice == null || !hasAllVoted()) {
      return;
    }

    allowProposal = false;

    List<Integer> agreedPlayers = agreedPlayers();
    boolean totallyAgreed = agreedPlayers.size() > currentVote.size() / 2;

    GameStatusUpdate gameStatusUpdate =
        GameStatusUpdate.newBuilder()
            .setIndex(index.incrementAndGet())
            .setVotedResult(
                GameStatusUpdate.VotedResult.newBuilder()
                    .setTotallyAgreed(totallyAgreed)
                    .addAllAgreed(agreedPlayers)
                    .build())
            .build();
    dispatchAndLog(gameStatusUpdate);

    if (totallyAgreed) {
      allowProposal = false;
      for (int i = 0; i < taskVote.size(); i++) {
        taskVote.set(i, null);
      }
      return;
    }

    round++;
    if (round == room.getMaximumRound()) {
      triggerMissionResult(false);
      return;
    }

    newRound();
  }

  private void newRound() {
    leader = leader + 1;
    if (leader == currentVote.size()) {
      leader = 0;
    }

    lastChoice = null;
    for (int i = 0; i < currentVote.size(); i++) {
      currentVote.set(i, VoteForPlayerRequest.Voting.UNKNOWN);
    }

    GameStatusUpdate gameStatusUpdate =
        GameStatusUpdate.newBuilder()
            .setIndex(index.incrementAndGet())
            .setRoundStart(
                GameStatusUpdate.RoundStart.newBuilder().setRound(round).setLeader(leader).build())
            .build();
    dispatchAndLog(gameStatusUpdate);

    allowProposal = true;
  }

  private void triggerMissionResult(boolean success) {
    GameStatusUpdate gameStatusUpdate =
        GameStatusUpdate.newBuilder()
            .setIndex(index.incrementAndGet())
            .setMissionResult(success)
            .build();
    dispatchAndLog(gameStatusUpdate);

    lastChoice = null;
    allowProposal = true;
    mission++;
    round = 0;


    if (false) {
      GameStatusUpdate gameStatusUpdate3 =
          GameStatusUpdate.newBuilder()
              .setIndex(index.incrementAndGet())
              .setLadyTestStarted(GameStatusUpdate.LadyTestStarted.newBuilder().build())
              .build();
      dispatchAndLog(gameStatusUpdate3);
    } else {
      newRound();
    }
  }

  private boolean hasAllVoted() {
    return currentVote.stream().allMatch(voting -> voting != VoteForPlayerRequest.Voting.UNKNOWN);
  }

  private List<Integer> agreedPlayers() {
    List<Integer> agreePlayers = new ArrayList<>();
    for (int i = 0; i < currentVote.size(); i++) {
      if (currentVote.get(i).equals(VoteForPlayerRequest.Voting.AGREE)) {
        agreePlayers.add(i);
      }
    }

    return agreePlayers;
  }

  private void dispatchAndLog(GameStatusUpdate gameStatusUpdate) {
    messageDispatcher.dispatch(gameStatusUpdate);
    gameStatusUpdateList.add(gameStatusUpdate);
  }

  private static boolean isBlue(Character character) {
    switch (character) {
      case MERLIN:
      case PERCIVAL:
      case ARTHUR_SERVANT:
        return true;
    }

    return false;
  }
}
