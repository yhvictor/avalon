package com.avalon.service;

import com.avalon.domain.GameManager;
import com.avalon.proto.base.ResponseError;
import com.avalon.proto.game.AssassinateRequest;
import com.avalon.proto.game.AvalonGameServiceGrpc;
import com.avalon.proto.game.GameStatusUpdate;
import com.avalon.proto.game.LadyResponse;
import com.avalon.proto.game.LadyTestRequest;
import com.avalon.proto.game.PollRequest;
import com.avalon.proto.game.ProposeRequest;
import com.avalon.proto.game.VoteForPlayerRequest;
import com.avalon.proto.game.VoteForTaskRequest;
import com.avalon.util.ResponseException;
import com.avalon.util.StatusOr;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

public class AvalonGameService extends AvalonGameServiceGrpc.AvalonGameServiceImplBase {
  private final GameManager gameManager;

  @Inject
  AvalonGameService(GameManager gameManager) {
    this.gameManager = gameManager;
  }

  @Override
  public void pollGameStatus(
      PollRequest request, StreamObserver<GameStatusUpdate> responseObserver) {
    try {
      gameManager.pollGameStatus(request.getAuth(), request.getGameId(), responseObserver);
    } catch (ResponseException responseException) {
      ResponseError responseError =
          ResponseError.newBuilder().setError(responseException.getMessage()).build();
      responseObserver.onNext(
          GameStatusUpdate.newBuilder().setResponseError(responseError).build());
      responseObserver.onCompleted();
    }
  }

  @Override
  public void propose(ProposeRequest request, StreamObserver<ResponseError> responseObserver) {
    ResponseError response =
        StatusOr.success(null)
            .transform(
                ignored -> {
                  gameManager.propose(
                      request.getAuth(), request.getGameId(), request.getProposal());
                  return null;
                })
            .to(ignore -> ResponseError.newBuilder().build(), error -> error);

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void voteForPlayer(
      VoteForPlayerRequest request, StreamObserver<ResponseError> responseObserver) {
    ResponseError response =
        StatusOr.success(null)
            .transform(
                ignored -> {
                  gameManager.voteForPlayer(
                      request.getAuth(), request.getGameId(), request.getVoting());
                  return null;
                })
            .to(ignore -> ResponseError.newBuilder().build(), error -> error);

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void voteFoTask(
      VoteForTaskRequest request, StreamObserver<ResponseError> responseObserver) {
    ResponseError response =
        StatusOr.success(null)
            .transform(
                ignored -> {
                  gameManager.voteFoTask(
                      request.getAuth(), request.getGameId(), request.getSuccess());
                  return null;
                })
            .to(ignore -> ResponseError.newBuilder().build(), error -> error);

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void ladyTest(LadyTestRequest request, StreamObserver<LadyResponse> responseObserver) {
    LadyResponse response =
        StatusOr.success(null)
            .transform(
                ignored ->
                    gameManager.ladyTest(request.getAuth(), request.getGameId(), request.getWhom()))
            .to(
                isBlue -> LadyResponse.newBuilder().setIsBlue(isBlue).build(),
                error -> LadyResponse.newBuilder().setResponseError(error).build());

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void assassinate(
      AssassinateRequest request, StreamObserver<ResponseError> responseObserver) {
    ResponseError response =
        StatusOr.success(null)
            .transform(
                ignored -> {
                  gameManager.assassinate(
                      request.getAuth(), request.getGameId(), request.getWhom());
                  return null;
                })
            .to(ignore -> ResponseError.newBuilder().build(), error -> error);

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}
