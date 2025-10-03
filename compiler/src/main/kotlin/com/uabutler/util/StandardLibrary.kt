package com.uabutler.util

enum class StandardLibraryFunctions(val identifier: String) {
    VECTOR_TO_WIRE("vector_to_wire"),
    WIRE_TO_VECTOR("wire_to_vector"),
    LEFT_PAD("left_pad"),
    BOOLEAN_TO_INT("boolean_to_int"),
    INDEX_LIST("index_list"),
    REPLICATE("replicate"),
    UNPAIR("unpair"),
    VECTOR_MAP("vector_map"),
    VECTOR_ZIP("vector_zip"),
    COMBINATIONAL_VECTOR_FOLD("combinational_vector_fold"),
    REPLICATED_FOLD("replicated_fold"),
    VECTOR_ANY("vector_any"),
    STREAM_ANY("stream_any"),
    ;
}

val standardLibary = """

interface pair(T: interface, U: interface) {
    first: T;
    second: U;
}

interface boolean wire

interface byte wire[8]

interface character wire[8]

interface valid(T: interface) {
    value: T;
    valid: boolean;
}

interface last(T: interface) {
    value: T;
    last: boolean;
}

interface conditional(T: interface) {
    condition: boolean;
    value: T;
}

function ${StandardLibraryFunctions.VECTOR_TO_WIRE.identifier}() i: wire[1] => o: wire { i[0] => o; }

function ${StandardLibraryFunctions.WIRE_TO_VECTOR.identifier}() i: wire => o: wire[1] { i => o[0]; }

function ${StandardLibraryFunctions.LEFT_PAD.identifier}(
    original: integer,
    padding: integer,
) i: wire[original] => o: wire[original + padding] {
    i => o[0:original - 1];
    literal(padding, 0) => o[original:original + padding - 1];
}

function ${StandardLibraryFunctions.BOOLEAN_TO_INT.identifier}(size: integer) i: wire => o: wire[size] {
    i => ${StandardLibraryFunctions.WIRE_TO_VECTOR.identifier}() => ${StandardLibraryFunctions.LEFT_PAD.identifier}(1, size - 1) => o;
}

function ${StandardLibraryFunctions.INDEX_LIST.identifier}(list_size: integer, index_size: integer) null => o: wire[index_size][list_size] {
    literal(index_size, list_size - 1) => o[list_size - 1];

    if (list_size > 1) {
        declare recursivefun: ${StandardLibraryFunctions.INDEX_LIST.identifier}(index_size, list_size - 1) => o[0:list_size - 2];
    }
}

function ${StandardLibraryFunctions.REPLICATE.identifier}(I: interface, factor: integer) i: I => o: I[factor] {
    i => o[0];
    if (factor > 1) {
        i => ${StandardLibraryFunctions.REPLICATE.identifier}(I, factor - 1) => o[1:factor - 1];
    }
}

function ${StandardLibraryFunctions.UNPAIR.identifier}(
    T: interface,
    U: interface,
    V: interface,
    operation: T, U => V
) i: pair(T, U) => o: V {
    i.first, i.second => operation => o;
}

function ${StandardLibraryFunctions.VECTOR_MAP.identifier}(I: interface, O: interface, size: integer, operation: I => O) i: I[size] => o: O[size]
{
    if (size > 0) {
        i[size - 1] => operation => o[size - 1];

        if (size > 1) {
            i[0:size - 2] => ${StandardLibraryFunctions.VECTOR_MAP.identifier}(I, O, size - 1, operation) => o[0:size - 2];
        }
    }
}

function ${StandardLibraryFunctions.VECTOR_ZIP.identifier}(
    I: interface,
    J: interface,
    vector_size: integer,
) i1: I[vector_size], i2: J[vector_size] => o: pair(I, J)[vector_size] {
    i1[0] => o[0].first;
    i2[0] => o[0].second;

    if (vector_size > 1) {
        i1[1:vector_size - 1], i2[1:vector_size - 1]
            => ${StandardLibraryFunctions.VECTOR_ZIP.identifier}(I, J, vector_size - 1)
            => o[1:vector_size - 1];
    }
}

function ${StandardLibraryFunctions.COMBINATIONAL_VECTOR_FOLD.identifier}(
    T: interface,
    U: interface,
    size: integer,
    operation: T, U => U,
) i: T[size], init: U => o: U {
    if (size == 1) {
        i[0], init => operation => o;
    } else {
        i[0], init => operation => declare updated_state: U;
        i[1:size - 1], updated_state => ${StandardLibraryFunctions.COMBINATIONAL_VECTOR_FOLD.identifier}(T, U, size - 1, operation) => o;
    }
}

function ${StandardLibraryFunctions.REPLICATED_FOLD.identifier}(
    T: interface,
    U: interface,
    size: integer,
    operation: T, U => U,
) i: T[size], init: U => o: U {
    if (size == 1) {
        i[0], init => operation => o;
    } else {
        i[0], init => operation => declare updated_state: U;
        i[1:size - 1], updated_state => ${StandardLibraryFunctions.REPLICATED_FOLD.identifier}(T, U, size - 1, operation) => o;
    }
}

function ${StandardLibraryFunctions.VECTOR_ANY.identifier}(size: integer) i: boolean[size] => o: boolean {
    declare false_v: literal(1, 0);
    i, false_v[0] => ${StandardLibraryFunctions.COMBINATIONAL_VECTOR_FOLD.identifier}(boolean, boolean, size, or()) => o;
}

function ${StandardLibraryFunctions.STREAM_ANY.identifier}() i: boolean() => o: boolean() {
    declare current: register(boolean);

    i, current => or() => current => o;
}

"""