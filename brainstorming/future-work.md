# Future Work

- Eliminate bugs
  - https://dl.acm.org/doi/abs/10.1145/3503222.3507701
- NFAs
- How to allow the user to define arbitrary handshakes.
- Parameter interfaces
  - Sets of parameters are defined by the users.
  - If two functions share the same parameter interfaces, then `a() => b()` will automatically use the same parameters from `a` in `b`.
- Interface References
  - One common patter is a top level module "passing" the wires from a top level IP block down to a module who uses it.
  - We should think of a cleaner way to support this
- A build system / imports
  - This might also include a vague concept of visibility