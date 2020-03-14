package com.avalon.service;

import com.avalon.domain.RoomManager;
import com.avalon.domain.UserManager;
import com.avalon.proto.base.ResponseError;
import com.avalon.proto.service.AvalonRoomServiceGrpc;
import com.avalon.proto.service.ChangeSeatRequest;
import com.avalon.proto.service.CreateRoomRequest;
import com.avalon.proto.service.CreateRoomResponse;
import com.avalon.proto.service.CreateUserRequest;
import com.avalon.proto.service.CreateUserResponse;
import com.avalon.proto.service.JoinRoomRequest;
import com.avalon.proto.service.ListRoomRequest;
import com.avalon.proto.service.ListRoomResponse;
import com.avalon.proto.service.RoomUpdate;
import com.avalon.proto.service.StartGameRequest;
import com.avalon.util.ResponseException;
import com.avalon.util.StatusOr;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

public class AvalonRoomService extends AvalonRoomServiceGrpc.AvalonRoomServiceImplBase {

  private final UserManager userManager;
  private final RoomManager roomManager;

  @Inject
  AvalonRoomService(UserManager userManager, RoomManager roomManager) {
    this.userManager = userManager;
    this.roomManager = roomManager;
  }

  @Override
  public void createUser(
      CreateUserRequest request, StreamObserver<CreateUserResponse> responseObserver) {
    CreateUserResponse response =
        StatusOr.success(null)
            .transform(ignored -> userManager.createUser(request.getUser()))
            .to(
                auth -> CreateUserResponse.newBuilder().setAuth(auth).build(),
                error -> CreateUserResponse.newBuilder().setResponseError(error).build());

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void createRoom(
      CreateRoomRequest request, StreamObserver<CreateRoomResponse> responseObserver) {
    CreateRoomResponse response =
        StatusOr.success(null)
            .transform(ignored -> roomManager.createRoom(request.getAuth(), request.getRoom()))
            .to(
                room -> CreateRoomResponse.newBuilder().setRoom(room).build(),
                error -> CreateRoomResponse.newBuilder().setResponseError(error).build());

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void listRooms(
      ListRoomRequest request, StreamObserver<ListRoomResponse> responseObserver) {
    ListRoomResponse response =
        ListRoomResponse.newBuilder().addAllRoom(roomManager.listRoom()).build();

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void joinRoom(JoinRoomRequest request, StreamObserver<RoomUpdate> responseObserver) {
    try {
      roomManager.joinRoom(request.getAuth(), request.getRoomName(), responseObserver);
    } catch (ResponseException responseException) {
      ResponseError responseError =
          ResponseError.newBuilder().setError(responseException.getMessage()).build();
      responseObserver.onNext(
          RoomUpdate.newBuilder()
              .setRoomClosedUpdate(
                  RoomUpdate.RoomClosedUpdate.newBuilder().setResponseError(responseError).build())
              .build());
      responseObserver.onCompleted();
    }
  }

  @Override
  public void changeSeat(
      ChangeSeatRequest request, StreamObserver<ResponseError> responseObserver) {
    ResponseError response =
        StatusOr.success(null)
            .transform(
                ignored -> {
                  roomManager.assignSeat(
                      request.getAuth(), request.getRoomName(), request.getSeat());
                  return null;
                })
            .to(ignored -> ResponseError.newBuilder().build(), error -> error);
  }

  @Override
  public void startGame(StartGameRequest request, StreamObserver<ResponseError> responseObserver) {
    super.startGame(request, responseObserver);
  }
}
