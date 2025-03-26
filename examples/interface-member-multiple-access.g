interface data()
{
    first: wire[8];
    second: wire[8];
}

interface payload()
{
    data: data();
    metadata: wire[8];
}

function test1() i: payload() => o: wire[8]
{
    i.data.first => o;
}

function test2() i: payload() => o: wire[8]
{
    i.data.second => o;
}
