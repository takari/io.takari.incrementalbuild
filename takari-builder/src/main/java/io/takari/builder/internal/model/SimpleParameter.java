/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.model;

import io.takari.builder.Parameter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class SimpleParameter extends AbstractParameter {

    private static final String[] EMPTY = {};

    private final Parameter annotation;

    SimpleParameter(MemberAdapter element, TypeAdapter type) {
        super(element, type);
        this.annotation = element.getAnnotation(Parameter.class);
    }

    @Override
    public Parameter annotation() {
        return annotation;
    }

    @Override
    public boolean required() {
        return annotation != null ? annotation.required() : false;
    }

    public String[] value() {
        return annotation != null ? annotation.value() : EMPTY;
    }

    public String[] defaultValue() {
        return annotation != null ? annotation.defaultValue() : EMPTY;
    }

    @Override
    public void accept(BuilderMetadataVisitor visitor) {
        visitor.visitSimple(this);
    }

    //
    //
    //

    static final Map<String, Function<String, ?>> converters;

    static {
        Map<String, Function<String, ?>> _converters = new LinkedHashMap<>();

        _converters.put(String.class.getCanonicalName(), s -> s);
        _converters.put(Boolean.class.getCanonicalName(), Boolean::valueOf);
        _converters.put(boolean.class.getCanonicalName(), Boolean::parseBoolean);
        _converters.put(int.class.getCanonicalName(), Integer::parseInt);
        _converters.put(Integer.class.getCanonicalName(), Integer::parseInt);
        _converters.put(long.class.getCanonicalName(), Long::parseLong);
        _converters.put(Long.class.getCanonicalName(), Long::parseLong);
        _converters.put(URL.class.getCanonicalName(), s -> {
            try {
                return new URL(s);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        });
        _converters.put(URI.class.getCanonicalName(), s -> {
            try {
                return new URI(s);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        });

        converters = Collections.unmodifiableMap(_converters);
    }

    public static boolean isSimpleType(TypeAdapter type) {
        return converters.containsKey(type.qualifiedName());
    }

    public static Function<String, ?> getConverter(TypeAdapter type) {
        Function<String, ?> converter = converters.get(type.qualifiedName());
        assert converter != null;
        return converter;
    }
}
