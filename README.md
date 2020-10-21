# github-api-lib

A small, very simple library with bits and pieces of Github's GraphQL and REST API, in one place for my own convenience.

## Usage

[![Clojars Project](https://img.shields.io/clojars/v/eamonnsullivan/github-api-lib.svg)](https://clojars.org/eamonnsullivan/github-api-lib)

You will need a Github access token with `repo` permissions. This is one way to provide that value:
```
(def token (System/getenv "GITHUB_ACCESS_TOKEN"))
```
### Pull Requests

```
(require '[eamonnsullivan.github-api-lib.pull-requests :as pr])
```

All of these methods return a map of information about the new or updated pull request or comment, such as the `:body` (in markdown), `:title`, `:permalink` or whether the pull request `:isDraft` or `:mergeable`.

#### Create a new pull request
```
(def options {:title "A title for the pull request"
              :body "The body of the pull request"
              :base "main or master, usually"
              :branch "the name of the branch you want to merge"
              :draft true})
(def new-pr-url (:permalink (pr/create-pull-request
                 token
                 "https://github.com/eamonnsullivan/github-pr-lib" options)))
```
The `:title`, `:base` and `:branch` are mandatory. You can omit the `:body`, and `:draft` defaults to true.

#### Update a pull request
```
(def updated {:title "A new title"
              :body "A new body"})
(pr/update-pull-request token new-pr-url updated)
```
#### Mark a pull request as ready for review
```
(pr/mark-ready-for-review token new-pr-url)
```
#### Comment on a pull request
Only handles issue comments on pull requests at the moment. The body text can use Github-style markdown.
```
;; returns the permalink for the comment
(def comment-link (pr/add-pull-request-comment token new-pr-url "Another comment."))
```
#### Edit an issue comment
```
(pr/edit-pull-request-comment token comment-link
                           "The new body for the comment, with *some markdown* and `stuff`.")
```
#### Close a pull request
```
(pr/close-pull-request token new-pr-url)
```
#### Reopen a pull request
```
(pr/reopen-pull-request token new-pr-url)
```
#### Merge a pull request
```
;; All of these fields are optional. The merge-method will default to "SQUASH".
;; The merge will fail if the pull-request's URL can't be found, if the pull
;; request's head reference is out-of-date or if there are conflicts.
(def merge-options {:title "A title or headline for the commit."
                    :body "The commit message body."
                    :mergeMethod "MERGE" or "REBASE" or "SQUASH"
                    :authorEmail "someone@somwhere.com"})
(pr/merge-pull-request token new-pr-url merge-options)
```
#### Misc. info
```
;; Various bits of information, such as whether it is mergeable or a draft.
(pr/get-pull-request-info token new-pr-url)
```

### Searching for topics

```clojure
(require '[eamonnsullivan.github-api-lib.repo-search :as rs])
(def result (rs/get-repos token "my-org" ["topic1" "topic2"]))
(spit "repos.edn" result)
```
The EDN returned will contain basic information about the repos found. For example:

```edn
[{:name "project1",
  :description
  "A description",
  :url "https://github.com/my-org/project1",
  :sshUrl "git@github.com:my-org/project1.git",
  :updatedAt "2020-04-09T11:01:55Z",
  :languages ["Javascript" "Python" "HTML"]}
 {:name "project2",
  :description "A description for project2",
  :url "https://github.com/my-org/project2",
  :sshUrl "git@github.com:my-org/project2.git",
  :updatedAt "2020-04-09T11:02:28Z",
  :languages ["Clojure" "ClojureScript"]}
]
```

## Development Notes

To run the project's tests:

    $ clojure -M:test:runner

To check test coverage:

    $ clojure -M:test:coverage

To build a deployable jar of this library:

    $ clojure -Spom               # to update any dependencies
    $ clojure -M:jar

To install the library locally:

    $ clojure -M:install

To deploy it to Clojars -- needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment variables or Maven settings:

    $ clojure --M:deploy

## License

Copyright © 2020 Eamonn

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
