package com.avalon.domain;

import com.google.protobuf.GeneratedMessageV3;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;

public class MessageDispatcher<T extends GeneratedMessageV3> {
  @GuardedBy("this")
  private final Set<StreamObserver<T>> streamObservers = new HashSet<>();

  void add(StreamObserver<T> streamObserver) {
    streamObservers.add(streamObserver);
  }

  void dispatch(T roomUpdate) {
    clear();
    streamObservers.forEach(streamObserver -> streamObserver.onNext(roomUpdate));
  }

  void clear() {
    streamObservers.removeIf(
        streamObserver -> ((ServerCallStreamObserver<?>) streamObserver).isCancelled());
  }

  void watchDog() {

  }
}
