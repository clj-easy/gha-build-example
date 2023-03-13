(require '[babashka.tasks :as t] '[cheshire.core :as json])

(defn -main
  "In babashka because we want to write this once only on GitHub Actions.
  Maybe there is an eqivalent github action out there we could use.
  Or mayb we should write our own.
  But just testing for now.

  Trace info goes to stderr.

  Writes `true` to stdout if `gh-event-name` is `push` and `gh-commit-sha` belongs to a PR."
  [gh-event-name gh-commit-sha]
  (.println *err* (str "event-name: " gh-event-name))
  (.println *err* (str "sha: " gh-commit-sha))
  (println
    (boolean (when (= "push" gh-event-name)
               (let [prs (-> (t/shell {:out :string
                                       :extra-env {"PAGER" "cat"}}
                                      "gh search prs" gh-commit-sha "--json" "number,repository")
                             :out
                             (json/parse-string true)
                             doall)]
                 (.println *err* (str "found prs: " (pr-str prs)))
                 (seq prs))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
