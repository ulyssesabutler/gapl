interface payload()
{
    data: wire[32][8];
    metadata: wire[32];
}

function combine() i: payload() => o: wire[32]
{
    i.data[1], i.metadata => function right_shift() => declare t: wire[32];

    t => function register() => o;
}
