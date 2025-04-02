function test1() i: wire[8] => o: wire[8]
{
    i => o;
}

function test2() i: wire[8] => o: wire[8]
{
    i => function test1() => o;
}
