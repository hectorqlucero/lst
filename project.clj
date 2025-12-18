(defproject org.clojars.hector/lein-template.lst "0.1.15"
  :description "LST Skeleton Web App"
  :url "http://github.com/hectorqlucero/lst"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :deploy-repositories [["clojars" {:url "https://repo.clojars.org"
                                    :username :env/CLOJARS_USERNAME
                                    :password :env/CLOJARS_PASSWORD
                                    :sign-releases false}]]
  :eval-in-leiningen true)
