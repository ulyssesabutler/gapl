interface sub_payload()
{
    first: wire[8];
    second: wire[8];
}

interface payload()
{
    a: sub_payload();
    b: sub_payload();
}

interface data_stream()
{
    data: payload()[8];
    metadata: wire[16];
}

function sub() i: data_stream() => o: data_stream()
{
    i => o;
}

function test() i: data_stream() => o: data_stream()
{
    declare t: data_stream();
    i => function sub() => t;
    t => o;
}
