(ns eamonnsullivan.github-api-lib.utils)

(defn parse-repo
  "Parse a repository url (a full url or just the owner/name part) and
  return a map with :owner and :name keys."
  [url]
  (let [matches (re-matches #"(https://github.com/)?([^/]*)/([^/]*).*$" url)
        [_ _ owner name] matches]
    (if (and owner name (not-empty owner) (not-empty name))
      {:owner owner :name name}
      nil)))

(defn pull-request-number
  "Get the pull request number from a full or partial URL."
  [pull-request-url]
  (let [matches (re-matches #"(https://github.com/)?[^/]*/[^/]*/pull/([0-9]*)" pull-request-url)
        [_ _ number] matches]
    (if (not-empty number)
      (Integer/parseInt number)
      nil)))

(defn parse-comment-url
  "Get the comment number and pull request url from an issue comment URL."
  [comment-url]
  (let [matches (re-matches #"(https://github.com/)?([^/]*)/([^/]*)/pull/([0-9]*)#issuecomment-([0-9]*)" comment-url)
        [_ _ owner name number comment] matches]
    (if (and (not-empty owner)
             (not-empty name)
             (not-empty number)
             (not-empty comment))
      {:pullRequestUrl (format "https://github.com/%s/%s/pull/%s" owner name number)
       :issueComment comment}
      nil)))
