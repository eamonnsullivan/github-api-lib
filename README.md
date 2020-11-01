# github-api-lib

A small, very simple library with bits and pieces of Github's GraphQL and REST API, in one place for my own convenience.

## Usage

[![Clojars Project](https://img.shields.io/clojars/v/eamonnsullivan/github-api-lib.svg)](https://clojars.org/eamonnsullivan/github-api-lib)

You will need a Github access token with `repo` permissions. This is one way to provide that value:
```clojure
(def token (System/getenv "GITHUB_ACCESS_TOKEN"))
```
### Pull Requests

```clojure
(require '[eamonnsullivan.github-api-lib.pull-requests :as pr])
```

All of these methods return a map of information about the new or updated pull request or comment, such as the `:body` (in markdown), `:title`, `:permalink` or whether the pull request `:isDraft` or `:mergeable`.

#### Create a new pull request
```clojure
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
```clojure
(def updated {:title "A new title"
              :body "A new body"})
(pr/update-pull-request token new-pr-url updated)
```
#### Mark a pull request as ready for review
```clojure
(pr/mark-ready-for-review token new-pr-url)
```
#### Comment on a pull request
Only handles issue comments on pull requests at the moment. The body text can use Github-style markdown.
```clojure
;; returns the permalink for the comment
(def comment-link (pr/add-pull-request-comment token new-pr-url "Another comment."))
```
#### Edit an issue comment
```clojure
(pr/edit-pull-request-comment token comment-link
                           "The new body for the comment, with *some markdown* and `stuff`.")
```
#### Close a pull request
```clojure
(pr/close-pull-request token new-pr-url)
```
#### Reopen a pull request
```clojure
(pr/reopen-pull-request token new-pr-url)
```
#### Merge a pull request
```clojure
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
```clojure
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

### Getting files in repos

Retrieve information about a file in a repository, on a particular branch. You can use "HEAD" for the branch to retrieve a file from the default branch. The information returns includes `:byteSize` and `:text`.

```clojure
(require '[eamonnsullivan.github-api-lib.files :as files])
(files/get-file token "eamonnsullivan" "github-api-lib" "HEAD" "README.md")

{:commitResourcePath
 "/eamonnsullivan/github-api-lib/commit/0805f4b95f5e01275e5962e0f8ed23def5129419",
 :byteSize 4296,
 :filepath "README.md",
 :abbreviatedOid "0805f4b",
 :isBinary false,
 :oid "0805f4b95f5e01275e5962e0f8ed23def5129419",
 :commitUrl
 "https://github.com/eamonnsullivan/github-api-lib/commit/...",
 :isTruncated false,
 :text
 "# github-api-lib\n\nA small, very simple..."}
```

You can also try several files and the first one found is returned.
```clojure
(files/get-first-file token "eamonnsullivan" "github-api-lib" "HEAD"
["build.sbt" ".nvmrc" "deps.edn" "project.edn"])

{:commitResourcePath
 "/eamonnsullivan/github-api-lib/commit/74c3092ef552681a7fa5c1a96b3a11479b4f0a28",
 :byteSize 1257,
 :filepath "deps.edn",
 :abbreviatedOid "74c3092",
 :isBinary false,
 :oid "74c3092ef552681a7fa5c1a96b3a11479b4f0a28",
 :commitUrl
 "https://github.com/eamonnsullivan/github-api-lib/commit/...",
 :isTruncated false,
 :text
 "{:paths [\"src\" \"resources\"]\n :deps ..."}
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
