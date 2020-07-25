(ns gungnir-playground.core
  (:require
   [buddy.hashers :as hashers]
   [ragtime.jdbc]
   [ragtime.repl]
   [gungnir.changeset :refer [changeset]]
   [gungnir.database :refer [make-datasource! *database*]]
   [gungnir.query :as q]
   [gungnir.model]
   [ragtime.core]))

;;
;; [Database]
;;
;; Gungnir `make-datasource!` supports the following values
;; * DATABASE_URL - The universal database url used by services such as Heroku / Render
;; * JDBC_DATABASE_URL - The standard Java Database Connectivity URL
;; * HikariCP configuration map - https://github.com/tomekw/hikari-cp#configuration-options

;; Gunir also has a `set-datasource!` function, if you want to create the
;; datasource yoursef.

(def datasource-opts
  "Very simple HikariCP configuration map. Be sure to start the
  Postgresl Docker container by running `docker-compose up -d` in the
  root directory.

  Configuration options:
  https://github.com/tomekw/hikari-cp#configuration-options"
  {:adapter       "postgresql"
   :username      "postgres"
   :password      "postgres"
   :database-name "postgres"
   :server-name   "localhost"
   :port-number   7432})

;; Initialize the datasource.  Normally you'd want to manage this through a
;; state manager like Integrant, Component, or Mount

(comment
  (make-datasource! datasource-opts))

;;
;; [Migrations]
;;
;; Currently Gungnir does not a built-in migrations tool (future feature). In
;; this demo we'll use Ragtime.
;;
;; Migrations are located in gungnir_playground/resources/migrations
;;
;; The following migrations will be run:
;; * Enable UUID
;; * Enable auto updating of the `updated_at` column
;; * Create "user" table
;; * Create "post" table
;; * Create "comment" table
;;
;; Relations
;; * comment belongs_to post
;; * comment belongs_to user
;; * post belongs_to user
;; * post has_many comment
;; * user has_many comment
;; * user has_many post

(defn migrate! []
  (ragtime.repl/migrate
   {:datastore (ragtime.jdbc/sql-database {:datasource *database*})
    :migrations (ragtime.jdbc/load-resources "migrations")}))

(comment
  ;; Run the migrations
  (migrate!))

;;
;; [Models]
;;
;; Now that we've defined our Postgresql database structure, we now need to
;; describe it to Gungnir. This is done using Malli schemas and the defmethod
;; `gungnir/model`. Each table has their own model which describe their
;; fields. Extra Gungnir specific model / field options can be added.
;;
;; Field options:
;;
;; * :primary-key - Tells Gungnir that this is the primary key of the
;; Model. Every model must always have 1 primary key defined
;;
;; * :auto - Any field with the `:auto` key will not be modified by
;; Gungnir. This is useful for for any columns that are filled in
;; automatically. For example `created_at` and `updated_at`.

(comment
  (gungnir.model/register!
   {:user
    [:map
     [:user/id {:primary-key true} uuid?]
     [:user/email
      [:re {:error/message "Invalid email"} #".+@.+\..+"]]
     [:user/password [:string {:min 6}]]
     [:user/created-at {:auto true} inst?]
     [:user/updated-at {:auto true} inst?]]}))

;; [Changesets]
;;
;; Changesets are used to validate, create, and update rows. Gungnir uses
;; qualified keywords to determine which model to use. e.g. if a record has the
;; key `:user/email`, it will use the `:user` model. If it has the `:post/title`
;; key, it will use the `:post` model.
;;
;; * Validation: Make sure that all data in a changeset is correct,
;; conforming to the model you describe. If a changeset is invalid, it will have
;; a key `:changeset/errors` containing the failing key with an error message.

(comment
  (changeset {:user/email "foo@bar.baz"
              :user/password "qweqw"})
  ;; => #:changeset{:errors {:user/password ["should be at least 6 characters"]} ,,, }

  (changeset {:user/email "foo@bar.baz"
              :user/password "qweqwe"})
  ;; => #:changeset{:errors nil ,,, }
  )

;; * External
;;
;; In the real world you will be receiving datastructures from outside of your
;; application. Often this data will also not be the same format as your
;; model (not qualified keywords, underscores instead of dashes). Gungnir adds a
;; `gungnir/cast` function to cast a map to a specific model map. It'll also
;; filter out any keys that are unknown to that model.

(comment
  (-> {"email" "foo@bar.baz"
       "password" "qweqwe"}
      (gungnir.changeset/cast :user))
  ;; => #:user{:email "foo@bar.baz", :password "qweqwe"}

  (-> {"email" "foo@bar.baz"
       "password" "qweqwe"
       "unknown-key" 123}
      (gungnir.changeset/cast :user))
  ;; => #:user{:email "foo@bar.baz", :password "qweqwe"}
  )

;; * Saving: If the `:changeset/errors` key is `nil` then you are ready to save
;; the changeset. Trying to save an invalid changeset will simply return the
;; changeset again without changing the database. If the changeset has no
;; errors, and the insertion is successful, it will return the newly created
;; row.

(comment
  (q/save!
   (changeset {:user/email "foo@bar.baz"
               :user/password "qweqw"}))
  ;; => #:changeset{:errors {:user/password ["should be at least 6 characters"]} ,,, }

  (q/save!
   (changeset {:user/email "foo@bar.baz"
               :user/password "qweqwe"}))
  ;; => #:user{:id #uuid "a73b46cc-2848-4405-a819-b6e8738007ef",
  ;;           :email "foo@bar.baz",
  ;;           :password "qweqwe",
  ;;           :created-at #inst "2020-07-20T20:20:06.112492000-00:00",
  ;;           :updated-at #inst "2020-07-20T20:20:06.112492000-00:00"}
  )

;;
;; [Query]
;;
;; Querying is primarily done with the following three functions
;;
;; * q/find!
;; Return a single record by its primary key. Or return nil.
;;
;; * q/find-by!
;; Return a single record which match specific key / values, or of `table`. Or
;; return nil
;;
;; * q/all!
;; Return a multiple records which match specific key / values, or of
;; `table`. Or return an empty vector

(comment
  (q/find! :user #"insert-primary-key-of-user")

  (q/find-by! :user/email "foo@bar.baz")
  ;; => #:user{:email "foo@bar.baz" ,,,}

  (q/all! :user/email "foo@bar.baz")
  ;; => [#:user{:email "foo@bar.baz" ,,,}]
  )

;; Since Gungnir is based on HoneySQL, we can modify these queries beforehand,
;; extending them as needed. The `gungnir.query` namespace has aliases to
;; HoneySQL helpers, so you don't need to require that separately.

(comment
  ;; Create 3 users
  (-> {:user/email "user-1@mail.com" :user/password "qweqwe"} (changeset) (q/save!))
  (-> {:user/email "user-2@mail.com" :user/password "qweqwe"} (changeset) (q/save!))
  (-> {:user/email "user-3@mail.com" :user/password "qweqwe"} (changeset) (q/save!))

  ;; Note, using the `:user` table instead of key value pairs
  (-> (q/order-by [:user/email :desc])
      (q/limit 2)
      (q/all! :user))
  ;; => [#:user{:email "user-3@mail.com" ,,,}
  ;;     #:user{:email "user-2@mail.com" ,,,}]
  )

;;
;; [Validators]
;;
;; Validators are extra, map wide validations. Since Malli fields are isolated
;; from eachother, we need a way to compare them in specific cases. Validators
;; can be added to changesets for extra custom checks.
;; Validators are maps which take three keys.
;;
;; * :validator/key
;; The the field's key in the map. This will be used for mapping errors.
;;
;; * :validator/fn
;; The function to check the validation. If this function return `true`, then
;; the validation passes. If it returns `false`. It has failed, and the
;; changeset will return this validator in the `:changeset/errors` key
;;
;; * :validator/message
;; In case of validation failure, this message will appear in `:changeset/errors`
;;
;;
;; Field options:
;;
;; * :virtual A key which is part of a model, but not stored in the
;; database. This can be used for certain validations such as password
;; confirmation checks.

(comment
  (gungnir.model/register!
   {:user
    [:map
     [:user/id {:primary-key true} uuid?]
     [:user/email
      [:re {:error/message "Invalid email"} #".+@.+\..+"]]
     [:user/password [:string {:min 6}]]
     ;; >>> ADD `:user/password-confirmation`
     [:user/password-confirmation {:virtual true} [:string {:min 6}]]
     [:user/created-at {:auto true} inst?]
     [:user/updated-at {:auto true} inst?]]})

  (defn- password-match? [m]
    (= (:user/password m)
       (:user/password-confirmation m)))

  ;; Define Gungnir validator named `:user/password-match?`
  (defmethod gungnir.model/validator :user/password-match? [_]
    {:validator/key :user/password-confirmation
     :validator/fn password-match?
     :validator/message "Passwords don't match"})

  (-> {:user/email "foo@bar.baz"
       :user/password "qweqwe"
       :user/password-confirmation "123123"}
      (changeset [:user/password-match?])
      :changeset/errors)
  ;; => #:user{:password-confirmation ["Passwords don't match"]}
  )

;;
;; [Hooks]
;;
;; Sometimes you want to modify certain fields before or after saving / reading
;; them. This can be done with the following hooks
;;
;; * gungnir.model/before-save
;;
;; Modify the column value before saving to the database.
;;
;; * gungnir.model/before-read (TODO RENAME after-read)
;;
;; Modify the column value after reading from the database.
;;
;; * gungnir.model/before-read
;;
;; Modify the column value before reading from the database.
;;

(comment
  (gungnir.model/register!
   {:user
    [:map
     [:user/id {:primary-key true} uuid?]
     ;; >>> ADD `:before-save` `:before-read` hooks
     ;; always lowercase `:user/email` when reading / writing
     [:user/email {:before-save [:string/lower-case]
                   :before-read [:string/lower-case]}
      [:re {:error/message "Invalid email"} #".+@.+\..+"]]
     [:user/password [:string {:min 6}]]
     [:user/password-confirmation {:virtual true} [:string {:min 6}]]
     [:user/created-at {:auto true} inst?]
     [:user/updated-at {:auto true} inst?]]})

  ;; Now regardless of uppercase / lowercase letters, emails will always be cast
  ;; to lowercase when writing and reading.
  (-> {:user/email "SomE-UseR@mAiL.cOM" :user/password "qweqwe"} (changeset) (q/save!))
  (q/find-by! :user/email "some-USER@MAIL.com")
  (q/find-by! :user/email "some-user@mail.com")
  ;;
  )

;; You can also create custom hooks. For example you'd never want to save a
;; password unencrypted to the database.

(comment
  (gungnir.model/register!
   {:user
    [:map
     [:user/id {:primary-key true} uuid?]
     [:user/email {:before-save [:string/lower-case]
                   :before-read [:string/lower-case]}
      [:re {:error/message "Invalid email"} #".+@.+\..+"]]
     ;; >>> ADD `:before-save` `:bcrypt'
     [:user/password {:before-save [:bcrypt]} [:string {:min 6}]]
     [:user/password-confirmation {:virtual true} [:string {:min 6}]]
     [:user/created-at {:auto true} inst?]
     [:user/updated-at {:auto true} inst?]]})

  ;; Define our custom `gungnir/before-save` hook. Where `v` is the value of the
  ;; column. Here we simple `buddy.hashers/derive` function to encrypt the value
  (defmethod gungnir.model/before-save :bcrypt [_ v]
    (hashers/derive v))

  (-> {:user/email "encrypted@mail.com" :user/password "secret!"} (changeset) (q/save!))
  ;; => #:user{:password "bcrypt+sha512$387a0d10115bb66c98c2a4b908f77e1e,,," ,,,}
  )

;;
;; [Relations]
;;

(comment
  (def model-user
    [:map
     ;; >>> ADD `:has-many` relation to `:post`
     {:has-many {:post :user/posts
                 :comment :user/comments}}
     [:user/id {:primary-key true} uuid?]
     [:user/email {:before-save [:string/lower-case]
                   :before-read [:string/lower-case]}
      [:re {:error/message "Invalid email"} #".+@.+\..+"]]
     [:user/password {:before-save [:bcrypt]} [:string {:min 6}]]
     [:user/password-confirmation {:virtual true} [:string {:min 6}]]
     [:user/created-at {:auto true} inst?]
     [:user/updated-at {:auto true} inst?]])

  (def model-post
    [:map
     {:belongs-to {:user :post/user-id}
      :has-many {:comment :post/comments}}
     [:post/id {:primary-key true} uuid?]
     [:post/title string?]
     [:post/content string?]
     [:post/user-id uuid?]
     [:post/created-at {:auto true} inst?]
     [:post/updated-at {:auto true} inst?]])

  (def model-comment
    [:map
     {:belongs-to {:user :comment/user-id
                   :post :comment/post-id}}
     [:comment/id {:primary-key true} uuid?]
     [:comment/content string?]
     [:comment/user-id uuid?]
     [:comment/post-id uuid?]
     [:comment/created-at {:auto true} inst?]
     [:comment/updated-at {:auto true} inst?]])

  (gungnir.model/register!
   {:user model-user
    :post model-post
    :comment model-comment}))

(defn create-user [email]
  (-> {:user/email email
       :user/password "qweqwe"}
      (changeset)
      (q/save!)))

(defn create-comment [user-id post-id content]
  (-> {:comment/user-id user-id
       :comment/post-id post-id
       :comment/content content}
      (changeset)
      (q/save!)))

(defn create-post [user-id title content]
  (-> {:post/user-id user-id
       :post/title title
       :post/content content}
      (changeset)
      (q/save!)))

(defn create-user-with-posts-comments
  "Create a user with the email `email`, and create 3 posts that belong to that user."
  [email]
  (let [user (create-user email)]
    (let [post (create-post (:user/id user) "post-1" "content-1")]
      (create-comment (:user/id user) (:post/id post) "comment-1-1")
      (create-comment (:user/id user) (:post/id post) "comment-1-2"))
    (let [post (create-post (:user/id user) "post-2" "content-2")]
      (create-comment (:user/id user) (:post/id post) "comment-2-1")
      (create-comment (:user/id user) (:post/id post) "comment-2-2")
      (create-comment (:user/id user) (:post/id post) "comment-2-3"))
    (let [post (create-post (:user/id user) "post-3" "content-3")]
      (create-comment (:user/id user) (:post/id post) "comment-3-1")
      (create-comment (:user/id user) (:post/id post) "comment-3-2")
      (create-comment (:user/id user) (:post/id post) "comment-3-3"))))

(comment
  (create-user-with-posts-comments "user-posts@mail.com")

  ;; Find a user by the given email. Select the `:user/posts` key, and deref it
  ;; to retrieve all of the users posts.
  (-> (q/find-by! :user/email "user-posts@mail.com")
      :user/posts
      (deref))

  ;; Once again, Gungnir is based on HoneySQL, so we can modify the relational
  ;; atom's query using `swap!`. For example let's say we wanted to limit it to
  ;; two posts instead of all posts.

  (-> (q/find-by! :user/email "user-posts@mail.com")
      :user/posts
      (swap! q/limit 2)
      (deref)
      (count))
  ;; => 2


  ;; Gungnir also provides a `q/load!` function which takes a record and any
  ;; amount of relational keys. Gungnir will then deref those keys without
  ;; taking them out of the record. Combined with `update` you could load the
  ;; relations in the same map, but still modify the outcome easily.
  (-> (q/find-by! :user/email "user-posts@mail.com")
      (update :user/posts swap! q/limit 2)
      (q/load! :user/posts))
  ;; => #:user{:id #uuid "c2635659-5b72-4696-a58a-81a73c2c2f35"
  ;; =>        :posts [#:post{,,,}
  ;; =>                #:post{,,,}]
  ;; =>        :comments <relation-atom>}


  ;; Or load multiple relations at once
  (-> (q/find-by! :user/email "user-posts@mail.com")
      (q/load! :user/posts :user/comments))
  ;; => #:user{:id #uuid "c2635659-5b72-4696-a58a-81a73c2c2f35"
  ;; =>        :posts [#:post{,,,}
  ;; =>                ,,,]
  ;; =>        :comments [#:comment{,,,}
  ;; =>                   ,,,]}


  ;; We can also go back from a post to the user it belongs to, the same way.
  (-> (q/find-by! :user/email "user-posts@mail.com")
      :user/posts
      ;; Get the user's posts
      (deref)
      (first)
      :post/user
      ;; Get the post's user
      (deref))

  ;; Get all comments of the user's first post
  (-> (q/find-by! :user/email "user-posts@mail.com")
      :user/posts
      (deref)
      (first)
      (:post/comments)
      (deref)))
