package com.uabutler.util

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

function vector_to_wire() i: wire[1] => o: wire { i[0] => o; }

function wire_to_vector() i: wire => o: wire[1] { i => o[0]; }

function left_pad(
    original: integer,
    padding: integer,
) i: wire[original] => o: wire[original + padding] {
    i => o[0:original - 1];
    literal(padding, 0) => o[original:original + padding - 1];
}

function boolean_to_int(size: integer) i: wire => o: wire[size] {
    i => wire_to_vector() => left_pad(1, size - 1) => o;
}

function index_list(list_size: integer, index_size: integer) null => o: wire[index_size][list_size] {
    literal(index_size, list_size - 1) => o[list_size - 1];

    if (list_size > 1) {
        declare recursivefun: index_list(index_size, list_size - 1) => o[0:list_size - 2];
    }
}

function replicate(I: interface, factor: integer) i: I => o: I[factor] {
    i => o[0];
    if (factor > 1) {
        i => replicate(I, factor - 1) => o[1:factor - 1];
    }
}

function unpair(
    T: interface,
    U: interface,
    V: interface,
    operation: T, U => V
) i: pair(T, U) => o: V {
    i.first, i.second => operation => o;
}

function vector_map(I: interface, O: interface, size: integer, operation: I => O) i: I[size] => o: O[size]
{
    if (size > 0) {
        i[size - 1] => operation => o[size - 1];

        if (size > 1) {
            i[0:size - 2] => vector_map(I, O, size - 1, operation) => o[0:size - 2];
        }
    }
}

function vector_zip(
    I: interface,
    J: interface,
    vector_size: integer,
) i1: I[vector_size], i2: J[vector_size] => o: pair(I, J)[vector_size] {
    i1[0] => o[0].first;
    i2[0] => o[0].second;

    if (vector_size > 1) {
        i1[1:vector_size - 1], i2[1:vector_size - 1]
            => vector_zip(I, J, vector_size - 1)
            => o[1:vector_size - 1];
    }
}

function combinational_vector_fold(
    T: interface,
    U: interface,
    size: integer,
    operation: T, U => U,
) i: T[size], init: U => o: U {
    if (size == 1) {
        i[0], init => operation => o;
    } else {
        i[0], init => operation => declare updated_state: U;
        i[1:size - 1], updated_state => combinational_vector_fold(T, U, size - 1, operation) => o;
    }
}

function replicated_fold(
    T: interface,
    U: interface,
    replication_factor: integer,
    operation: T, U => U,
) i: last(T[replication_factor]) => o: valid(U) {
    declare state: register(U);
    i.value, state
        => replicated_fold(T, U, replication_factor, operation)
        => state
        => o.value;

    i.last => register(boolean) => o.valid;
}

function vector_any(size: integer) i: boolean[size] => o: boolean {
    declare false_v: literal(1, 0);
    i, false_v[0] => combinational_vector_fold(boolean, boolean, size, or()) => o;
}

function stream_any() i: boolean() => o: boolean() {
    declare current: register(boolean);

    i, current => or() => current => o;
}

"""