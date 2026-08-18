# Regular Expression Matching

## Regular Expression
Let's try to implement a specific regular expression in gapl, hard coding the DFA.

Specifically, this regex

```text
[A-Z0-9._%+-]+@(?:[A-Z0-9-]+\.)+[A-Z]{2,}
```

This regex is designed to match valid email addresses.
Email addresses are divided into the local portion (before the @) and the host URL (after the @).
In this simple example, the local portion can contain the character `A`-`Z`, `0`-`9`, or `_`, `.`, or `-`.
The URL is a sequence of `.` seperated subdomains, followed by a TLD, which is made up of only letters, `A`-`Z`.

```text
┌─────┐      ┌────┐    ┌────┐    ┌────┐      ┌────┐      ┌──────┐
│START├─────►│ N1 ├───►│ N2 ├───►│ N3 ├─────►│ N4 │─────►│FINISH│
└─────┘ A-Z  └───┬┘ @  └──┬─┘ .  └─┬──┘ A-Z  └───┬┘ A-Z  └──────┘
        0-9   ▲  │      ▲ │ ▲      │          ▲  │               
         _    └──┘      └─┘ └──────┘          └──┘               
         .     A-Z      A-Z    A-Z             A-Z               
         -     0-9      0-9    0-9                               
                _        -      -                                
                .                                                
                -                                                
```

## GAPL Code

### Helper Functions
First, let's define some helper functions.

#### Library
```text
function replicate<I>(parameter factor: integer) i: I => o: I[factor] {
    i => o[factor - 1];
    
    if (factor > 1) {
        i => function replicate(factor - 1) => o[0:factor - 2];
    }
}
```

#### Edge Conditions
```text
function is_character_in_range()
    candidate: character,
    range: pair<character, character>
        => result: boolean
{
    candidate, range.first  => declare lower: function greater_than_equal();
    candidate, range.second => declare upper: function less_than_equal();
    
    lower, upper => function and() => result;
}

function is_character_in_range_pair_wrapper() i: pair<character, pair<character, character>> => o: boolean
{
    i.first, i.second => function is_character_in_range() => result;
}

function is_character_in_set(parameter set_size: integer)
    candidate: character,
    set: pair<character, character>[set_size]
        => result: boolean
{
    candidate => declare candidate_vector: function replicate<character>(set_size);
    
    candidate_vector, set
        => function zip<character, pair<character, character>>(set_size);
        => function vector_map<pair<character, pair<character, character>>, boolean>(set_size)
        => function any(set_size);
}

function is_local_character() i: character => o: boolean {
    // This notation is made up, and doesn't exist in the grammar
    [{'A', 'Z'}, {'0', '9'}, {'.', '.'}, {'_', '_'}, {'-', '-'}] => declare set: pair<character, character>[5];
    i, set => function is_character_in_set(5) => o;
}

function is_url_character() i: character => o: boolean {
    // This notation is made up, and doesn't exist in the grammar
    [{'A', 'Z'}, {'0', '9'}, {'-', '-'}] => declare set: pair<character, character>[3];
    i, set => function is_character_in_set(3) => o;
}

function is_tld_character() i: character => o: boolean {
    i, {'A', 'Z'} => function is_character_in_range() => o;
}

function is_url_separator() i: character => o: boolean {
    i, '.' => function equals(8) => o;
}

function is_email_separator() i: character => o: boolean {
    i, '@' => function equals(8) => o;
}
```

#### NFA Helpers
```text
function nfa_node(parameter input_size: integer) i: boolean[input_size] => o: boolean {
    function state: register<boolean>();
    i => function any() => state => o;
}

function nfa_edge(parameter condition: function character => boolean)
    source_node: boolean,
    candidate: character
        => destination_node: boolean
{
    candidate => declare valid_character: function condition;
    source_node, valid_character => function and() => destination_node;
}
```

### Main Function
```text
function nfa() i: character => o: boolean
{
    declare n1_inputs: boolean[2] => function nfa_node(2) => declare n1_output;
    declare n2_inputs: boolean[3] => function nfa_node(3) => declare n2_output;
    declare n3_inputs: boolean[1] => function nfa_node(1) => declare n3_output;
    declare n4_inputs: boolean[1] => function nfa_node(1) => declare n4_output;
    declare n5_inputs: boolean[2] => function nfa_node(2) => declare n5_output;
    
    true,      i => declare start_to_n1: function nfa_edge(function is_local_character()) => n1_inputs[0];
    n1_output, i => declare n1_to_n1:    function nfa_edge(function is_local_character()) => n1_inputs[1];
    
    n1_output, i => declare n1_to_n2:    function nfa_edge(function is_email_separator()) => n2_inputs[0];
    n2_output, i => declare n2_to_n2:    function nfa_edge(function is_url_character())   => n2_inputs[1];
    n3_output, i => declare n3_to_n2:    function nfa_edge(function is_url_character())   => n2_inputs[2];
    
    n2_output, i => declare n2_to_n3:    function nfa_edge(function is_url_separator())   => n3_inputs[0];
    
    n3_output, i => declare n3_to_n4:    function nfa_edge(function is_url_character())   => n4_inputs[0];
    
    n4_output, i => declare n4_to_n5:    function nfa_edge(function is_url_character())   => n5_inputs[0];
    n5_output, i => declare n5_to_n5:    function nfa_edge(function is_url_character())   => n5_inputs[1];
    
    n5_output => o;
}
```

## Unstructured GAPL

First, we're going to use the same library and edge conditions.
That said, we're going to add one.

### Library
```text
function <T, U>fold(parameter size: integer, parameter operation: function T, U => U) init: U, collection: stream<T> => result: U
```

Next, we'll define interface used to maintain state
```text
interface nfa {
    n1: boolean;
    n2: boolean;
    n3: boolean;
    n4: boolean;
    n5: boolean;
}

interface state {
    nodes: nfa;
    result: boolean;
}

function nfa_node(parameter input_size: integer) i: boolean[input_size] => o: boolean {
    i => function any() => o;
}

function nfa_edge(parameter condition: function character => boolean)
    source_node: boolean,
    candidate: character
        => destination_node: boolean
{
    candidate => declare valid_character: function condition;
    source_node, valid_character => function and() => destination_node;
}

function update_nfa(): current_state: state, char: character  => update: state
{
    declare n1_inputs: boolean[2] => function nfa_node(2) => update.nodes.n1;
    declare n2_inputs: boolean[3] => function nfa_node(3) => update.nodes.n2;
    declare n3_inputs: boolean[1] => function nfa_node(1) => update.nodes.n3;
    declare n4_inputs: boolean[1] => function nfa_node(1) => update.nodes.n4;
    declare n5_inputs: boolean[2] => function nfa_node(2) => update.nodes.n5;
    
    true,                   i => declare start_to_n1: function nfa_edge(function is_local_character()) => n1_inputs[0];
    current_state.nodes.n1, i => declare n1_to_n1:    function nfa_edge(function is_local_character()) => n1_inputs[1];
    
    current_state.nodes.n1, i => declare n1_to_n2:    function nfa_edge(function is_email_separator()) => n2_inputs[0];
    current_state.nodes.n2, i => declare n2_to_n2:    function nfa_edge(function is_url_character())   => n2_inputs[1];
    current_state.nodes.n3, i => declare n3_to_n2:    function nfa_edge(function is_url_character())   => n2_inputs[2];
    
    current_state.nodes.n2, i => declare n2_to_n3:    function nfa_edge(function is_url_separator())   => n3_inputs[0];
    
    current_state.nodes.n3, i => declare n3_to_n4:    function nfa_edge(function is_url_character())   => n4_inputs[0];
    
    current_state.nodes.n4, i => declare n4_to_n5:    function nfa_edge(function is_url_character())   => n5_inputs[0];
    current_state.nodes.n5, i => declare n5_to_n5:    function nfa_edge(function is_url_character())   => n5_inputs[1];
    
    update.nodes.n5, current_state.result => function or() => update.result.;
}

function main() i: stream<character> => o: boolean
{
    {[0, 0, 0, 0, 0], 0}, i => fold<character, state>(function update_nfa()) => o;
}
```