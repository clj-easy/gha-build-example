(require '[babashka.cli :as cli]
         '[babashka.tasks :as t]
         '[cheshire.core :as json]
         '[clojure.string :as str])

(defn ci-write-skip-tests-var
  "Determine if tests should be skipped and write result to GITHUB_OUTPUT skip_tests var

  Written babashka for conciseness and maintainability."
  [{:keys [commit-sha event-name commit-headline ref repo] :as github}]
  (let [prs (-> (t/shell {:out :string
                          :extra-env {"PAGER" "cat"}}
                         "gh search prs" commit-sha "--json" "number" "--repo" repo)
                :out
                (json/parse-string true)
                doall)
        is-in-pr (boolean (seq prs))
        is-pushing (= "push" event-name)
        is-publish-commit (str/starts-with? commit-headline "publish:")
        is-version-tag (str/starts-with? ref "ref/tags/v")
        skip-tests (or (not is-version-tag) ;; indicates invocation from publish, so allow tests to run
                       is-publish-commit ;; no need to run tests for commit that is part of publish flow
                       (and is-pushing is-in-pr) ;; tests will be triggered pull_request synchronize, no need to duplicate the effort
                       )]
    (println "inputs:" (pr-str github))
    (println "prs:" (pr-str prs))
    (println "is-in-pr" is-in-pr)
    (println "is-pushing" is-pushing)
    (println "is-publish-commit" is-publish-commit)
    (println "is-version-tag" is-version-tag)
    (println "skip-tests" skip-tests)
    (if-let [out-file (System/getenv "GITHUB_OUTPUT")]
      (spit out-file (str "skip_tests=" skip-tests) :append true)
      (throw (ex-info "GITHUB_OUTPUT env var not found" {})))))

(def spec (->> {:commit-sha {:desc "git commit sha, used to determine if in PR"}
                :event-name {:desc "github.event_name"}
                :commit-headline {:desc "current commit headline"}
                :ref {:desc "github.ref"}
                :repo {:desc "our github repository org/repo"}}
               (reduce-kv (fn [m k v]
                            (assoc m k (assoc v :require true))) {})))

(defn -main [& args]
  (ci-write-skip-tests-var (cli/parse-opts args {:spec spec})))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
