package com.avalon.util;

import com.avalon.proto.base.ResponseError;
import java.util.function.Function;

public class StatusOr<T> {

  public static <T> StatusOr<T> success(T t) {
    return new StatusOr<>(t, null);
  }

  public static <T> StatusOr<T> failure(String failure) {
    return new StatusOr<>(null, failure);
  }

  private final T t;
  private final String errorMesasge;

  private StatusOr(T t, String errorMesasge) {
    this.t = t;
    this.errorMesasge = errorMesasge;
  }

  public boolean isSuccessful() {
    return t != null;
  }

  public <OutputT> StatusOr<OutputT> transformStatus(Function<T, StatusOr<OutputT>> function) {
    if (isSuccessful()) {
      return function.apply(t);
    } else {
      return failure(this.errorMesasge);
    }
  }

  public <OutputT> StatusOr<OutputT> transform(
      FunctionThatThrows<T, OutputT, ResponseException> function) {
    if (isSuccessful()) {
      try {
        return StatusOr.success(function.apply(t));
      } catch (ResponseException e) {
        return StatusOr.failure(e.getMessage());
      }
    } else {
      return failure(this.errorMesasge);
    }
  }

  public <OutputT> OutputT to(
      Function<T, OutputT> successHandler, Function<ResponseError, OutputT> failureHandler) {
    if (isSuccessful()) {
      return successHandler.apply(t);
    } else {
      return failureHandler.apply(ResponseError.newBuilder().setError(errorMesasge).build());
    }
  }
}
