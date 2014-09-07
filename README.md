GSSrden
=======

A [GSS][gss] plugin for [Garden][garden].

Installation
------------

### Leiningen

```
[gssrden "0.1.0"]
```

### Gradle

```
compile "gssrden:gssrden:0.1.0"
```

### Maven
   
```
<dependency>
  <groupId>gssrden</groupId>
  <artifactId>gssrden</artifactId>
  <version>0.1.0</version>
</dependency>
```

Usage
-----

Install GSS as detailed [here][gss-install]. Direct your Garden-produced CSS
into a `.gss` file (say, `resources/gss/screen.gss`) and link to it as detailed 
in the GSS installation, i.e.

```
<link rel="stylesheet" type="text/gss" href="gss/screen.gss"></link>
``
    
or [Hiccup][hiccup] and similar templates:

```clojure
   [:link {:rel "stylesheet", :type "test/gss", :href "gss/screen.gss"}]
```
   
As of now, the GSSrden API consists of just the `constraints` macro. It can be 
used like this:

```clojure
(ns super-responsive.styles.screen
  (:require [garden.def :refer [defstyles]]
            [gssrden.core :refer [constraints]])
           
(defstyles screen
  [:body
    (constraints
      (== :width (:window :width))
      (== :height (:window :height)))
    {:background-color "red"}])
```
       
If you are on ClojureScript, you will have to use `:require-macros` instead.

### The constraints macro

`constraints` takes a number of constraint expressions and turns them into a 
valid Garden property map whose keys are GSS properties and values GSS 
constraint strings:

```clojure
(constraints
  (== :width (:body :width)
      :strength :medium
      :weight 1000)
  (<= :height (- (/ (:parent :$col-width) 2)
                 :$my-var 15)
      :strong))
  ;=> {:width "== body[width] !medium1000"
       :height
       "<= (::parent[$col-width] / 2
             - [$my-var] - 15) !strong"}
```

The constraints can take the following forms:

* `(eq-operator property goal-expression)`
* `(eq-operator property goal-expression :strength strength :weight weight)`
* `(eq-operator property goal-expression :s strength :w weight)`
* `(eq-operator property goal-expression :strength strength)`
* `(eq-operator property goal-expression :s strength)`
* `(eq-operator property goal-expression strength weight)`
* `(eq-operator property goal-expression strength)`

Where eq-operator is an (in)equality operator symbol, property is a GSS
property (a Garden key) and goal-expression is a linear function of the
properties of certain elements and GSS variables. As described in the
[GSS CCSS documentation][ccss-doc], strength can be one of

* `:weak` / `"weak"` / `weak`
* `:medium` / `"medium"` / `medium`
* `:strong` / `"strong"` / `strong`
* `:require` / `"require"` / `require`

and weight is just an integer.

You can get a property prop of element elem like this: `(:elem :prop)`.

In GSSrden custom constraint and element variables are keywords beginning
with $: `:$my-var`. The special pseudo selectors are provided as the
keywords `:window`, `:this` and `:parent`. You can use intrinsic properties
exactly like in GSS, by prefixing with intrinsic-: `:intrinsic-height`.

Note that due to the output being a map, it is not possible to declare
multiple constraint for a single property in one `constraints` form. You also
cannot do non-constraint property assigments in a `constraints` form (this is
intentional). Since a Garden rule can contain multiple maps, you can instead
do this:

```clojure
    [:li :a
      (constraints
        (>= :line-height 16))
      (constraints
        (<= :line-height (/ (:window :height) 2)))
      {:color "purple"}]
```
      
See the included tests and [Marginalia][marginalia] documentation for more 
insight into the inner life of GSSrden.
      
Future plans
------------

Some [QML][qml]-inspired conveniences:

* `center-in`
* `fill`
* implicit `:this`

The rest of GSS:

* Support `@stay`
* Add [VFL-like sugar][vfl-doc]
* Support `@if` and `@else`

The lack of raw CSS directive support in Garden complicates these tasks.

License
-------

Copyright © 2014 Pauli Jaakkola

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[gss]: http://gridstylesheets.org/
[garden]: https://github.com/noprompt/garden
[gss-install]: http://gridstylesheets.org/usage/
[hiccup]: https://github.com/weavejester/hiccup
[ccss-doc]: http://gridstylesheets.org/guides/ccss/
[marginalia]: https://github.com/gdeer81/marginalia
[qml]: http://en.wikipedia.org/wiki/QML
[vfl-doc]: http://gridstylesheets.org/guides/vfl/
