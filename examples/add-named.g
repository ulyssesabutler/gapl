function add_test() i1: wire[32], i2: wire[32] => o: wire[32]
{
    i1, i2 => declare addition: function add() => o;
}
