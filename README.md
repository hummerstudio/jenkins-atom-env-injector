# jenkins-atom-env-injector

Inject environment — port of **envinject** (45,085) as an atom.

An output whose key is a legal variable name *becomes* that variable, so this atom only returns `KEY=value`
lines. Illegal key names are skipped and listed, not exported as something surprising.

## Build and smoke test

```bash
./build.sh
./tools/smoke.sh
```
