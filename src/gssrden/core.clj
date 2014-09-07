(ns gssrden.core
  "A GSS plugin for Garden"
  (:require [clojure.string :as s]
            [clojure.core.match :refer [match]]))

;;; TODO: center-in, fill <= QML

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
  "Turn a constraint expression c into a vector [property value] where property
   is the constrained property and value is the output gss string:

    (== :width (body :width) :strengh :medium, :weight 1000))
    ;=> [:width \"== body[width] !medium1000\"]

   These can then be stuffed into the final constraint property map."
  [c]
  (match (vec c)
         [_ _ _] (vector (second c)
                         (str (first c) " "
                              (goal-expr (last c))))
         [_ _ _ (:or :strength :s) s (:or :weight :w) w]
         (let [[k expr] (handle-constraint (drop-last 4 c))]
           (vector k (str expr " !" (key->string s) w)))
         [_ _ _ (:or :strength :s) s] (handle-constraint
                                        (concat c [:weight ""]))
         [_ _ _ s w] (handle-constraint (concat (drop-last 2 c)
                                                [:strength s
                                                 :weight w]))
         [_ _ _ s] (handle-constraint (concat (drop-last c)
                                              [:strength s
                                               :weight ""]))))

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

   Where eq-operator is an (in)equality operator symbol, property is a GSS
   property (a Garden key) and goal-expression is a linear function of the
   properties of certain elements and GSS variables. As described in the
   GSS CCSS documentation, strength can be one of

   * `:weak` / `\"weak\"` / `weak`
   * `:medium` / `\"medium\"` / `medium`
   * `:strong` / `\"strong\"` / `strong`
   * `:require` / `\"require\"` / `require`

   and weight is just an integer.

   In GSSrden custom constraint and element variables are keywords beginning
   with $: `:$my-var`. The special pseudo selectors are provided as the
   keywords `:window`, `:this` and `:parent`. You can use intrinsic properties
   exactly like in GSS, by prefixing with intrinsic-: `:intrinsic-height`.

   If you are confused, look at the example above and consult the GSSrden and
   GSS documentation."
  [& cs]
  (apply array-map (mapcat handle-constraint cs)))

