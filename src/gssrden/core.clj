(ns gssrden.core
  "A GSS plugin for Garden"
  (:require [clojure.string :as s]
            [clojure.core.match :refer [match]]))

;;; * TODO: bypass :this <= QML (is this prudent?)
;;; * TODO: hiccup helper fn

;;; Internal utility functions
;;; ===========================================================================

(defn- garden-key?
  "True if k is a valid Garden key (a keyword, string or symbol)."
  [k]
  (or (keyword? k)
      (string? k)
      (symbol? k)))

(defn- key->string
  "Make Garden key k a string, i.e.

    (= (key->string :foo)
       (key->string \"foo\")
       (key->string 'foo)
       \"foo\""
  [k]
  (cond (keyword? k) (name k)
        (string? k) k
        (symbol? k) (str k)))

;;; The heavy lifting
;;; ===========================================================================

(defn- s-w-expr
  "Generate a GSS string to specify strength or strength and weight:

    (s-w-expr :strong 1000) ;=> \"!strong1000\"
    (s-w-expr :strong) ;=> \"!strong\"

  If strength is the empty string, return the empty string."
  ([s] (s-w-expr s ""))
  ([s w] (if-not (= s "")
           (str "!" (key->string s) w)
           "")))

(defn- goal-expr
  "Turn a goal expression expr (the right side of an (in)equality) into its GSS
   counterpart string. To illustrate:

    (goal-expr :$col-size) ;=> \"[col-size]\"
    (goal-expr (- (:window :width) (:#ads :width) 15))
    ;=> \"::window[width] - #ads[width] - 15\"
    (goal-expr (:body :width)) ;=> \"body[width]\"
    (goal-expr (:window :width)) ;=> \"::window[width]\"
    30 ;=> 30

   Non-tail recursive, but won't consume much stack anyway."
  [expr]
  (cond ;; Custom constraint variable
    ((every-pred garden-key? #(= (first (key->string %)) \$)) expr)
    (str "[" (key->string expr) "]")
    (coll? expr)
    (cond
      ;; Math operation
      (#{'+ '- '* '/} (first expr))
      (let [[operator & operands] expr
            math-expr (interpose operator
                                 (map goal-expr operands))]
        ;; operator precedence:
        (if (#{'+ '-} operator)
          (str "(" (s/join " " math-expr) ")")
          (s/join " " math-expr)))
      ;; Element property get
      ((every-pred coll? #(= (count %) 2)) expr)
      (let [[element property] expr]
        (str (case element
               ;; special pseudos:
               (:window :this :parent) (str "::" (key->string element))
               (key->string element))
             "[" (key->string property) "]")))
    :else expr))

(defn- handle-constraint
  "Turn a constraint expression c into a map {property value} where property
   is the constrained property and value is the output gss string:

    (== :width (body :width) :strengh :medium, :weight 1000))
    ;=> {:width \"== body[width] !medium1000\"}

   These can then be stuffed into the final constraint property map."
  [c]
  ;; Need to use vectors with quoted == since quoting the whole list form
  ;; would prevent center-target/fill-target from evaluating and
  ;; syntax-quoting would ns-qualify == into clojure.core/==. Furthermore,
  ;; a list starting with quoted == yields nil since '== is not a
  ;; function...
  (match (vec c)
         ;; "Full" syntax:
         [operator property goal (:or :strength :s) s (:or :weight :w) w]
         {property
           (s/trimr (s/join " " [operator (goal-expr goal) (s-w-expr s w)]))}

         ['center-in center-target (:or :strength :s) s (:or :weight :w) w]
         (into {}
               (map handle-constraint
                    [['== :center-x [center-target :center-x] s w]
                     ['== :center-y [center-target :center-y] s w]]))
         ['fill fill-target (:or :strength :s) s (:or :weight :w) w]
         (into {}
               (map handle-constraint
                    [['== :center-x [fill-target :center-x] s w]
                     ['== :center-y [fill-target :center-y] s w]
                     ['== :width [fill-target :width] s w]
                     ['== :height [fill-target :height] s w]]))

         ;; Shorter versions:
         [(:or 'center-in 'fill) _ (:or :strength :s) s] (handle-constraint
                                                           (concat c [:w ""]))
         [(:or 'center-in 'fill) _ s w] (handle-constraint
                                          (concat (drop-last 2 c) [:s s :w w]))
         [(:or 'center-in 'fill) _ s] (handle-constraint
                                        (concat (drop-last c) [:s s :w ""]))
         [(:or 'center-in 'fill) _] (handle-constraint (concat c [:s "" :w ""]))

         [_ _ _] (handle-constraint (concat c [:s "" :w ""]))
         [_ _ _ (:or :strength :s) s] (handle-constraint (concat c [:w ""]))
         [_ _ _ s w] (handle-constraint (concat (drop-last 2 c) [:s s :w w]))
         [_ _ _ s] (handle-constraint (concat (drop-last c) [:s s :w ""]))))

;;; Public API
;;; ===========================================================================

(defmacro constraints
  "Take the constraint expressions cs and turn them into a valid Garden
   property map whose keys are GSS properties and values GSS constraint
   strings:

    (constraints
      (== :width (:body :width)
          :strength :medium
          :weight 1000)
      (<= :height (- (/ (:parent :$col-width) 2)
                     :$my-var 15)
          :strong))
      ;=> {:width \"== body[width] !medium1000\"
           :height
           \"<= (::parent[$col-width] / 2
                 - [$my-var] - 15) !strong\"}

   The constraints can take the following forms:

   * `(eq-operator property goal-expression)`
   * `(eq-operator property goal-expression :strength strength :weight weight)`
   * `(eq-operator property goal-expression :s strength :w weight)`
   * `(eq-operator property goal-expression :strength strength)`
   * `(eq-operator property goal-expression :s strength)`
   * `(eq-operator property goal-expression strength weight)`
   * `(eq-operator property goal-expression strength)`
   * `(center-in center-target)`
   * `(fill fill-target)`
   * `center-in` and `fill` with strength and weight specified just like for
     the other forms.

   Where eq-operator is an (in)equality operator symbol, property is a GSS
   property (a Garden key) and goal-expression is a linear function of the
   properties of certain elements and GSS variables. As described in the
   GSS CCSS documentation, strength can be one of

   * `:weak` / `\"weak\"` / `weak`
   * `:medium` / `\"medium\"` / `medium`
   * `:strong` / `\"strong\"` / `strong`
   * `:require` / `\"require\"` / `require`

   and weight is just an integer.

   You can get a property prop of element elem like this: `(:elem :prop)`.

   `center-in` and `fill` are sugar inspired by QML and are equivalent to

    (constraints
      (== :center-x (center-target :center-x))
      (== :center-y (center-target :center-y)))

   and

    (constraints
      (== :center-x (fill-target :center-x))
      (== :center-y (fill-target :center-y))
      (== :width (fill-target :width))
      (== :height (fill-target :height)))

   In GSSrden custom constraint and element variables are keywords beginning
   with $: `:$my-var`. The special pseudo selectors are provided as the
   keywords `:window`, `:this` and `:parent`. You can use intrinsic properties
   exactly like in GSS, by prefixing with intrinsic-: `:intrinsic-height`.

   Note that due to the output being a map, it is not possible to declare
   multiple constraints for a single property in one `constraints` form.
   You also cannot do non-constraint property assigments in a `constraints` form
   (this is intentional). Since a Garden rule can contain multiple maps, you can
   instead do this:

    [:li :a
     (constraints
       (>= :line-height 16))
     (constraints
       (<= :line-height (/ (:window :height) 2)))
     {:color \"purple\"}]

   If you are confused, look at the example above and consult the GSSrden and
   GSS documentation."
  [& cs]
  (into {} (map handle-constraint cs)))

