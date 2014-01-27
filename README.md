Clojure starter pack for [vindinium](http://vindinium.org)

## Usage

```
$ lein repl
```

```clojure
> ; (re)load the code
> (require 'vindinium.core :reload)

> ; run a training game with 80 turns
> (vindinium.core/-main secretkey "training" 80)

> ; run 50 arena games
> (vindinium.core/-main secretkey "arena" 50)
```

## Implementation

You need to implement the `bot` function.
It takes an `input` argument - a map of data - print it to see what's inside.
It returns an move order - one of `north`, `south`, `east`, `west`, `stay`.
