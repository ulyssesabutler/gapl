# FSM examples — evidence for `../fsm-sugar.md`

Five small GAPL files. Three are working FSMs written in **today's** GAPL with no new features; two
are negative tests that pin down what the language rejects. Everything here was checked against the
tree at `172ae65`.

Build the CLI once:

```
./gradlew :compiler:installDist
```

Then, from the repo root:

```
./compiler/build/install/gapl/bin/gapl -o /tmp/out.v brainstorming/claude/fsm-sugar-examples/<file>.gapl
```

## The three that compile

| file | what it is | why it is here |
|---|---|---|
| `policer.gapl` | the token bucket policer from `applications.md` #6 | shows that cross-lifecycle state works today with plain `register`s; measures how little of the tedium is position tracking (4 of 52 lines) |
| `arbiter.gapl` | the round-robin selection core of `axis_3_to_1_arbiter.v` | shows tag dispatch via `mux` and a nested-`if_else` next state; its generated `case` has no `default` (see `fsm-sugar.md` §1.5) |
| `mutex.gapl` | the three-named-state FSM of `axis_mutual_exclusion.v` | the clearest specimen of the two shapes side by side: `mux`-style output dispatch and a `priority` next-state chain |

Notes on the reconstructions: both `arbiter.gapl` and `mutex.gapl` omit ready/valid, because GAPL has
no handshake — they are the selection logic only. `mutex.gapl` also routes its outputs through
declared intermediates rather than reading them back, because GAPL output ports are write-only
(`fsm-sugar.md` §1.6); the first version of this file did read them back and was rejected.

Only parameterless functions are emitted as Verilog modules, which is why `policer.gapl` carries a
`policer_top()` wrapper pinning `token_bucket`'s integer parameters.

## The two that do not

Both are expected failures and are the evidence for two of the design's load-bearing facts.

| file | expected error | establishes |
|---|---|---|
| `infix.gapl` | `This looks like a value expression … circuit expressions must reference a wire, an interface, or a function call instead` | `expression`'s operators are static-only; nothing lowers them to hardware (§1.2) |
| `transformer.gapl` | `Transformers are not supported yet` | the record/vector transformer syntax parses but is unimplemented (§1.3) |
