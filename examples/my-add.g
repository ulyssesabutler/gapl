function my_add() a1: wire[32], a2: wire[32] => o1: wire[32]
{
    a1 => o1;
}

function add_test() i1: wire[32], i2: wire[32] => o: wire[32]
{
    i1, i2 => function my_add() => o;
}
