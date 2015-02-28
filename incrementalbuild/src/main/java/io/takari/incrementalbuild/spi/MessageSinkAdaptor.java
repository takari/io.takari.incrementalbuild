package io.takari.incrementalbuild.spi;

import java.util.Collection;
import java.util.Map;


public interface MessageSinkAdaptor {

  void clear(Object resource);

  void record(Map<Object, Collection<Message>> allMessages,
      Map<Object, Collection<Message>> newMessages);

}
