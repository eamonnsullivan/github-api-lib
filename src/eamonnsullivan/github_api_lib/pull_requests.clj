(ns eamonnsullivan.github-api-lib.pull-requests
  (:require [eamonnsullivan.github-api-lib.core :as core]
            [eamonnsullivan.github-api-lib.repos :as repos]
            [clojure.data.json :as json]))

(defn pull-request-number
  "Get the pull request number from a full or partial URL."
  [pull-request-url]
  (let [matches (re-matches #"(https://github.com/)?[^/]*/[^/]*/pull/([0-9]*)" pull-request-url)
        [_ _ number] matches]
    (if (not-empty number)
      (Integer/parseInt number)
      (throw (ex-info (format "Could not parse pull request number from url: %s" pull-request-url) {})))))

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
      (throw (ex-info (format "Could not parse comment from url: %s" comment-url) {})))))

(defn get-pull-request-node-id
  "Get the node id of a pull request using the v3 REST api, optionally
  filtered by state (\"open\", \"closed\", or the default of \"all\")"
  [access-token owner repo-name pull-number state]
  (let [url (str "https://api.github.com/repos/" owner
                 "/" repo-name
                 "/pulls/" pull-number)
        response (core/http-get access-token url (merge (core/request-opts access-token)
                                                        {:accept "application/vnd.github.v3+json"
                                                         :query-params {"state" state}}))
        body (json/read-str (:body response) :key-fn keyword)]
    (:node_id body)))

(defn get-comment-node-id
  "Get the node id of a pull request comment using the v3 REST API."
  [access-token owner repo comment-number]
  (let [url (str "https://api.github.com/repos/" owner
                 "/" repo
                 "/issues/comments/" comment-number)
        response (core/http-get access-token url (merge (core/request-opts access-token)
                                                        {:accept "application/vnd.github.v3+json"}))
        body (json/read-str (:body response) :key-fn keyword)]
    (:node_id body)))

(defn get-pull-request-id
  "Find the unique ID of a pull request on the repository at the
  provided url. Set must-be-open? to true to filter the pull requests
  to those with a status of open. Throws a runtime exception if the
  pull request isn't found."
  ([access-token url]
   (get-pull-request-id access-token url false))
  ([access-token url must-be-open?]
   (let [repo (repos/parse-repo url)
         prnum (pull-request-number url)
         owner (:owner repo)
         name (:name repo)]
     (or
      (get-pull-request-node-id access-token owner name prnum (if must-be-open? "open" "all"))
      (throw (ex-info (format "Could not find pull request: %s" url) {}))))))

(defn get-issue-comment-id
  "Find the unique ID of an issue comment on a pull request. Returns nil if not found."
  [access-token comment-url]
  (let [repo (repos/parse-repo comment-url)
        owner (:owner repo)
        name (:name repo)
        comment (:issueComment (parse-comment-url comment-url))]
    (or
     (get-comment-node-id access-token owner name comment)
     (throw (ex-info (format "Could not find comment: %s" comment-url) {})))))


(defn get-pull-request-info
  "Find some info about a pull request.
  Available properties: :id, :title, :body, :baseRefOid,
  :headRefOid, :permalink, :author (:login, :url), :closed,
  :isDraft, :merged, :mergeable (MERGEABLE, CONFLICTING or UNKNOWN),
  :number, :repository (:id, :url), :state (CLOSED, MERGED or OPEN)."
  [access-token pull-request-url]
  (let [pr-id (get-pull-request-id access-token pull-request-url)]
    (when pr-id
      (-> (core/make-graphql-post
           access-token
           (core/get-graphql "pull-request-query")
           {:pullRequestId pr-id})
          :data
          :node))))

(defn modify-pull-request
  "Modify a pull request at the url with the provided mutation."
  ([access-token url mutation]
   (modify-pull-request access-token url mutation nil))
  ([access-token url mutation variables]
   (let [pr-id (get-pull-request-id access-token url)]
     (when pr-id
       (let [merged-variables (merge variables {:pullRequestId pr-id})]
         (core/make-graphql-post access-token mutation merged-variables))))))

(defn modify-comment
  "Modify a comment at the url with the provided mutation and variables."
  [access-token url mutation variables]
  (let [comment-id (get-issue-comment-id access-token url)]
    (when comment-id
      (let [merged-variables (merge variables {:commentId comment-id})]
        (core/make-graphql-post access-token mutation merged-variables)))))


(def create-pr-defaults {:draft true})

(defn create-pull-request
  "Create a pull request on Github repository.

   Arguments:
   * access-token -- the Github access token to use. Must have repo permissions.
   * url -- the URL of the repo (optional). The URL can omit the https://github.com/, e.g. owner/repo-name.
   * pull-request -- a map describing the pull request. Keys: :title, :base (the base branch),
     :branch (the branch you want to merge) and (if a URL isn't provided) the :owner (or organisation)
     and :name of the repo. Optional key :draft (default: true) indicates whether the pull request
     is in a draft state and not ready for review.

   Returns a map describing the pull request, including :title, :body, :permalink, :additions, :deletions
   and :revertUrl.
  "
  ([access-token url pull-request]
   (let [repo (repos/parse-repo url)]
     (when repo
       (create-pull-request access-token (merge pull-request repo)))))
  ([access-token pull-request]
   (let [{owner :owner
          repo-name :name
          title :title
          body :body
          base-branch :base
          merging-branch :branch
          draft :draft} (merge create-pr-defaults pull-request)
         repo-id (repos/get-repo-id access-token owner repo-name)
         variables {:repositoryId repo-id
                    :title title
                    :body body
                    :base base-branch
                    :branch merging-branch
                    :draft draft}]
     (when repo-id
       (-> (core/make-graphql-post
            access-token
            (core/get-graphql "create-pull-request-mutation") variables)
           :data
           :createPullRequest
           :pullRequest)))))

(defn update-pull-request
  "Update an existing pull request.

   Argments:
   * access-token -- the Github access token to use. Must have repo permissions.
   * pull-request-url -- the full (e.g., https://github.com/owner/name/pull/1) or
     partial (owner/name/pull/1) URL of the pull request.
   * updated -- a map describing the update. The keys: :title, :body.

   Returns a map describing the pull request, including :title, :body, :permalink,
   :additions, :deletions and :revertUrl.
  "
  [access-token pull-request-url updated]
  (-> (modify-pull-request
       access-token
       pull-request-url
       (core/get-graphql "update-pull-request-mutation")
       updated)
      :data
      :updatePullRequest
      :pullRequest))

(defn mark-ready-for-review
  "Mark a pull request as ready for review.

   This effectively just toggles the :draft property of the pull request to false.

   Arguments:
   * access-token -- the Github access token to use. Must have repo permissions.
   * pull-request-url -- the full (e.g., https://github.com/owner/name/pull/1) or
     partial (owner/name/pull/1) URL of the pull request.

   Returns a map describing the pull request, including :title, :body, :permalink,
   :additions, :deletions and :revertUrl.
  "
  [access-token pull-request-url]
  (-> (modify-pull-request
       access-token
       pull-request-url
       (core/get-graphql "mark-ready-for-review-mutation"))
      :data
      :markPullRequestReadyForReview
      :pullRequest))

(defn add-pull-request-comment
  "Add a top-level comment to a pull request.

   Arguments:
   * access-token -- the Github access token to use. Must have repo permissions.
   * pull-request-url -- the full (e.g., https://github.com/owner/name/pull/1) or
     partial (owner/name/pull/1) URL of the pull request.
   * comment-body -- the comment to add.

   Returns information about the comment, including its :url and :body.
  "
  [access-token pull-request-url comment-body]
  (-> (modify-pull-request
       access-token
       pull-request-url
       (core/get-graphql "add-comment-mutation")
       {:body comment-body})
      :data
      :addComment
      :commentEdge
      :node))

(defn edit-pull-request-comment
  "Changes the body of a comment.

   Arguments:
   * access-token -- the Github access token to use. Must have repo permissions.
   * comment-url -- e.g., the full (e.g., https://github.com/owner/name/pull/4#issuecomment-702092682) or
     partial (owner/name/pull/4#issuecomment-702092682) URL of the comment.
   * comment-body -- the new body of the comment.

   Returns information about the comment, including its :url and :body.
  "
  [access-token comment-url comment-body]
  (-> (modify-comment
       access-token
       comment-url
       (core/get-graphql "edit-comment-mutation")
       {:body comment-body})
      :data
      :updateIssueComment
      :issueComment))

(defn close-pull-request
  "Change the status of a pull request to closed.

   Arguments:
   * access-token -- the Github access token to use. Must have repo permissions.
   * pull-request-url -- the full (e.g., https://github.com/owner/name/pull/1) or
     partial (owner/name/pull/1) URL of the pull request.

   Returns a map describing the pull request,
   including :title, :body, :permalink, :additions, :deletions
   and :revertUrl."
  [access-token pull-request-url]
  (-> (modify-pull-request
       access-token
       pull-request-url
       (core/get-graphql "close-pull-request-mutation"))
      :data
      :closePullRequest
      :pullRequest))

(defn reopen-pull-request
  "Change the status of a pull request to open.

   Arguments:
   * access-token -- the Github access token to use. Must have repo permissions.
   * pull-request-url -- the full (e.g., https://github.com/owner/name/pull/1) or
     partial (owner/name/pull/1) URL of the pull request.

   Returns a map describing the pull request,
   including :title, :body, :permalink, :additions, :deletions
   and :revertUrl."
  [access-token pull-request-url]
  (-> (modify-pull-request
       access-token
       pull-request-url
       (core/get-graphql "reopen-pull-request-mutation"))
      :data
      :reopenPullRequest
      :pullRequest))

(defn merge-pull-request
  "Merge a pull request.

   Arguments:
   * access-token -- the Github access token to use. Must have repo permissions.
   * pull-request-url -- the full (e.g., https://github.com/owner/name/pull/1) or
     partial (owner/name/pull/1) URL of the pull request.
   * merge-options -- a map with keys that can include :title (the headline of the
     commit), :body (any body description of the commit), :mergeMethod (default
     \"SQUASH\", but can also be \"MERGE\" or \"REBASE\") and :authorEmail.
     All of these input fields are optional.

   Returns a map describing the pull request, including :title, :body, :permalink,
   :additions, :deletions and :revertUrl."
  ([access-token pull-request-url]
   (merge-pull-request access-token pull-request-url nil))
  ([access-token pull-request-url merge-options]
   (let [prinfo (get-pull-request-info access-token pull-request-url)
         expected-head-ref (:headRefOid prinfo)]
     (if expected-head-ref
       (let [opts (merge {:mergeMethod "SQUASH"} merge-options {:expectedHeadRef expected-head-ref})]
         (-> (modify-pull-request
              access-token
              pull-request-url
              (core/get-graphql "merge-pull-request-mutation")
              opts)
             :data
             :mergePullRequest
             :pullRequest))
       (throw (ex-info "Pull request not found" {:pullRequestUrl pull-request-url}))))))
