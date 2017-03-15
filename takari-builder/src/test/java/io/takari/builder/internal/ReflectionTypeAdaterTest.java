package io.takari.builder.internal;

import java.util.Collection;

import io.takari.builder.internal.Reflection.ReflectionType;
import io.takari.builder.internal.model.AbstractTypeAdapterTest;
import io.takari.builder.internal.model.MemberAdapter;

public class ReflectionTypeAdaterTest extends AbstractTypeAdapterTest {

  @Override
  protected Collection<MemberAdapter> listMembers(Class<?> type) {
    return new ReflectionType(type).getAllMembers();
  }

  //
  // Actual tests are defined in AbstractTypeAdapterTest and shared with APT
  //

}
