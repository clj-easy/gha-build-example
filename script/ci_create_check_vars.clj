(require '[babashka.tasks :as t]
         '[cheshire.core :as json]
         '[clojure.string :as str])

(defn is-pushing-in-pr [{:keys [commit-sha event-name repo]}]
  (let [prs (-> (t/shell {:out :string
                          :extra-env {"PAGER" "cat"}}
                         "gh search prs" commit-sha "--json" "number" "--repo" repo)
                :out
                (json/parse-string true)
                doall)
        is-in-pr (boolean (seq prs))
        is-pushing (= "push" event-name)]
    (println "prs:" (pr-str prs))
    (println "is-in-pr" is-in-pr)
    (println "is-pushing" is-pushing)
    (and is-pushing is-in-pr)))

(defn ci-write-skip-tests-var
  "Determine if tests should be skipped and write result to GITHUB_OUTPUT skip_tests var

  Written babashka for conciseness and maintainability."
  [{:keys [commit-message ref] :as github}]
  (let [is-publish-commit (str/starts-with? commit-message "publish:")
        is-version-tag (str/starts-with? ref "ref/tags/v")
        run-tests (or is-version-tag
                      (and (not is-publish-commit)
                           (not (is-pushing-in-pr github))))]
    (println "inputs:" (pr-str github))
    (println "is-publish-commit" is-publish-commit)
    (println "is-version-tag" is-version-tag)
    (println "run-tests" run-tests)
    (if-let [out-file (System/getenv "GITHUB_OUTPUT")]
      (spit out-file (str "run_tests=" run-tests) :append true)
      (throw (ex-info "GITHUB_OUTPUT env var not found" {})))))

(def spec (->> {:commit-sha {:desc "git commit sha, used to determine if in PR"}
                :event-name {:desc "github.event_name"}
                :commit-message {:desc "current commit message"}
                :ref {:desc "github.ref"}
                :repo {:desc "our github repository org/repo"}}))

(defn args-from-env []
  (reduce-kv (fn [m k {:keys [desc]}]
               (let [env-var (-> k
                                 name
                                 str/upper-case
                                 (str/replace "-" "_"))
                     env-value (System/getenv env-var)]
                 (if (not env-value)
                   (throw (ex-info (format "env-var %s required, desc: %s" env-var desc) {}))
                   (assoc m k env-value))
                 ))
             {}
             spec))

(defn -main [& _args]
  (ci-write-skip-tests-var (args-from-env)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
