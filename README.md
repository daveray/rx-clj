# rx-clj

Notional Clojure bindings for [RxJava](https://github.com/Netflix/RxJava). The main goal is to provide Rx versions of all the sequence operators in `clojure.core`, building on top of RxJava whenever possible. This will make it much more natural for a Clojure programmer to use RxJava when everything has the same "shape" as the Clojure functions they're familiar with. The biggies include:

* Order of arguments to sequence operators
* `Observable/zip` is just Clojure's `map` with multiple arguments
* Predicates with non-Boolean return values
* `Observable/toList` is just a whimpy version of `clojure.core/into`
* Consistent naming like `Observable/scan` to `reductions`, `Observable/skip` to `drop`, etc.


For example, in RxJava, the signature of `Observable.map` is `[xs f]`, but in Clojure, it's `[f & xs]`. Similarly, a filter predicate in Rx must return an honest-to-god `Boolean`, whereas Clojure's filter is happy with anything truthy. So instead of slightly unfamiliar code like this:

```clojure
  (-> (Observable/from (slurp "words.txt"))
      (.filter (comp boolean #{:a :e :i :o :u}))
      (.reduce {} (fn [m v] (update-in m [v] (fnil inc 0)))))
```

you write code like this:

```clojure
  (->> (rx/seq (slurp "words.txt"))
       (rx/filter keepers)
       (rx/reduce (fn [m v] (update-in m [v] (fnil inc 0))) {}))
```

In either case, the result is an observable sequence.

See test/rx_clj/core_test.clj

## Usage

This is just an idea. Don't use it. Slings and arrows welcomed.

## LICENSE

Copyright 2013 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

