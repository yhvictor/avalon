package com.avalon.util;

public interface FunctionThatThrows<I, O, E extends Exception> {
  O apply(I i) throws E;
}
