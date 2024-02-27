package io.takari.builder.internal.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public abstract class AbstractTypeAdapterTest {

    protected abstract Collection<MemberAdapter> listMembers(Class<?> type);

    protected MemberAdapter getMember(Class<?> type, String memberName) {
        return listMembers(type).stream() //
                .filter(m -> memberName.equals(m.getName())) //
                .findFirst()
                .get();
    }

    static class _ParameterTypesData {
        Map<String, String> map;

        @SuppressWarnings("rawtypes")
        Map rawMap;

        Map<?, ?> wildcardMap;

        Map<String, ?> wildcardValueMap;

        List<String> list;

        @SuppressWarnings("rawtypes")
        List rawList;

        @SuppressWarnings("rawtypes")
        List<List> listOfRaw;

        List<List<?>> listOfWildcard;

        Iterable<String> iterable;

        String[] array;
    }

    @Test
    public void testParameterTypes_map() {
        MemberAdapter member = getMember(_ParameterTypesData.class, "map");
        assertTrue(member.getType().isMap());
        List<TypeAdapter> parameterTypes = member.getParameterTypes();
        assertEquals(2, parameterTypes.size());
        assertTrue(parameterTypes.get(0).isSameType(String.class));
        assertTrue(parameterTypes.get(1).isSameType(String.class));
    }

    @Test
    public void testParameterTypes_rawMap() {
        MemberAdapter member = getMember(_ParameterTypesData.class, "rawMap");
        assertTrue(member.getType().isMap());
        List<TypeAdapter> parameterTypes = member.getParameterTypes();
        assertEquals(0, parameterTypes.size());
    }

    @Test
    public void testParameterTypes_wildcardMap() {
        MemberAdapter member = getMember(_ParameterTypesData.class, "wildcardMap");
        assertTrue(member.getType().isMap());
        List<TypeAdapter> parameterTypes = member.getParameterTypes();
        assertEquals(0, parameterTypes.size());
    }

    @Test
    public void testParameterTypes_wildcardValueMap() {
        MemberAdapter member = getMember(_ParameterTypesData.class, "wildcardValueMap");
        assertTrue(member.getType().isMap());
        List<TypeAdapter> parameterTypes = member.getParameterTypes();
        assertEquals(1, parameterTypes.size());
        assertTrue(parameterTypes.get(0).isSameType(String.class));
    }

    @Test
    public void testParameterTypes_list() {
        MemberAdapter member = getMember(_ParameterTypesData.class, "list");
        assertTrue(member.getType().isIterable());
        List<TypeAdapter> parameterTypes = member.getParameterTypes();
        assertEquals(1, parameterTypes.size());
        assertTrue(parameterTypes.get(0).isSameType(String.class));
    }

    @Test
    public void testParameterTypes_listOfRaw() {
        MemberAdapter member = getMember(_ParameterTypesData.class, "listOfRaw");
        assertTrue(member.getType().isIterable());
        List<TypeAdapter> parameterTypes = member.getParameterTypes();
        assertEquals(1, parameterTypes.size());
        assertTrue(parameterTypes.get(0).isSameType(List.class));
    }

    @Test
    public void testParameterTypes_listOfWildcard() {
        MemberAdapter member = getMember(_ParameterTypesData.class, "listOfWildcard");
        assertTrue(member.getType().isIterable());
        List<TypeAdapter> parameterTypes = member.getParameterTypes();
        assertEquals(1, parameterTypes.size());
        assertTrue(parameterTypes.get(0).isSameType(List.class));
    }

    @Test
    public void testParameterTypes_rawList() {
        MemberAdapter member = getMember(_ParameterTypesData.class, "rawList");
        assertTrue(member.getType().isIterable());
        List<TypeAdapter> parameterTypes = member.getParameterTypes();
        assertEquals(0, parameterTypes.size());
    }

    @Test
    public void testParameterTypes_iterable() {
        MemberAdapter member = getMember(_ParameterTypesData.class, "iterable");
        assertTrue(member.getType().isIterable());
        List<TypeAdapter> parameterTypes = member.getParameterTypes();
        assertEquals(1, parameterTypes.size());
        assertTrue(parameterTypes.get(0).isSameType(String.class));
    }

    @Test
    public void testParameterTypes_array() {
        MemberAdapter member = getMember(_ParameterTypesData.class, "array");
        assertTrue(member.getType().isArray());
        List<TypeAdapter> parameterTypes = member.getParameterTypes();
        assertEquals(1, parameterTypes.size());
        assertTrue(parameterTypes.get(0).isSameType(String.class));
    }

    //
    //
    //

    static class _InheritanceParent {
        String parentString;
    }

    static class _InheritanceChild extends _InheritanceParent {
        String childString;
    }

    @Test
    public void testMemberInheritance() throws Exception {
        List<MemberAdapter> members = new ArrayList<>(listMembers(_InheritanceChild.class));
        assertEquals(2, members.size());
        assertEquals("parentString", members.get(0).getName());
        assertEquals("_InheritanceParent", members.get(0).getDeclaringType().simpleName());
        assertEquals("childString", members.get(1).getName());
        assertEquals("_InheritanceChild", members.get(1).getDeclaringType().simpleName());
    }
}
