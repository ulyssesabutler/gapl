interface payload()
{
    data: wire[32];
    metadata: wire[16];
}

function sub() s1: payload(), s2: payload() => so: payload()
{
    s1 => so;
}

function test() t1: payload(), t2: payload() => to: payload()
{
    t1, t2 => function sub() => to;
}
