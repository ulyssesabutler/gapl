interface payload()
{
    data: wire[8];
    metadata: wire[8];
}

function test() i: payload() => o: payload()
{
    i => o;
}
