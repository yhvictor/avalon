package com.avalon.domain;

import com.avalon.proto.base.Auth;
import com.avalon.proto.service.User;
import com.avalon.util.ResponseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

public class UserManager {
  private final AtomicInteger atomicInteger = new AtomicInteger();

  @GuardedBy("this")
  private final Map<Integer, UserInfo> map = new HashMap<>();

  private final Set<String> nameSet = new HashSet<>();

  @Inject
  UserManager() {}

  public synchronized Auth createUser(User user) throws ResponseException {
    if (user.getUsername().isEmpty()) {
      throw new ResponseException("User name could not be empty");
    }

    if (nameSet.contains(user.getUsername())) {
      throw new ResponseException("User name already token");
    }

    int id = atomicInteger.incrementAndGet();
    Auth auth =
        Auth.newBuilder()
            .setId(id)
            .setToken(UUID.randomUUID().toString() + user.getUsername())
            .build();
    map.put(id, new UserInfo(auth, user));
    nameSet.add(user.getUsername());

    return auth;
  }

  public synchronized UserInfo validate(Auth auth) throws ResponseException {
    if (auth.getId() <= 0 && !map.containsKey(auth.getId())) {
      throw new ResponseException("Failed to auth");
    }

    UserInfo userInfo = map.get(auth.getId());
    if (!userInfo.auth.getToken().equals(auth.getToken())) {
      throw new ResponseException("Failed to auth");
    }

    return userInfo;
  }

  public static class UserInfo {
    public final Auth auth;
    public final User user;

    UserInfo(Auth auth, User user) {
      this.auth = auth;
      this.user = user;
    }
  }
}
