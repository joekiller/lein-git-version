(ns lein-git-version.plugin
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [leiningen.git-version :refer :all])
  (:import
   (java.io File)
   (java.util.regex Pattern)))

(defn- version-file
  [{:keys [git-version group name source-paths] :as project} config]
  (let [srcpath (first (filter seq (map #(re-find #".*src$" %) source-paths)))]
    (->> (conj []
               srcpath
               (str/replace
                (or (if (seq group) (str/replace group "." "/"))
                    name)
                "-" "_")
               (:filename config))
         (filter seq)
         (interpose "/")
         (apply str))))

(defn assoc-version
  [project version config]
  (reduce #(assoc-in %1 %2 version) project (:assoc-in-keys config)))

(defn middleware
  [{:keys [git-version name root] :as project}]
  (let [config (merge default-config git-version)
        fs       File/separator
        fsp      (Pattern/quote fs)
        version (get-git-version config)
        ns (str "(ns "
                (cond
                  (:root-ns config) (:root-ns config)
                  (:path config)
                  (let [path (:path config)]
                    (-> path
                        (str/replace root "")
                        (str/replace (re-pattern (str "^" fsp "src" fsp)) "")
                        (str/replace fs ".")))
                  :else name)
                ".version)\n")
        code (str
              ";; Do not edit.  Generated by lein-git-version plugin.\n"
              ns
              "(def timestamp " (get-git-ts config) ")\n"
              "(def version \"" version "\")\n"
              "(def gitref \"" (get-git-ref config) "\")\n"
              "(def gitmsg \"" (get-git-last-message config) "\")\n")
        proj-dir (.toLowerCase (.replace (:name project) \- \_))
        filename (if (:path config)
                   (if (.isAbsolute (io/file (:path config)))
                     (str (:path config) "/" (:filename config))
                     (str (:root project) "/" (:path config) "/" (:filename config)))
                   (version-file project config))]
    (if git-version
      (let [project* (-> project
                         (assoc-version version config)
                         (assoc :gitref (get-git-ref config)))]
        (spit filename code)
        project*)
      project)))

