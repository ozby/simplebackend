# A Simple Backend

Simple Backend is a framework for building backend in Kotlin while avoiding as much complexity as possible.

## Overview

Status: As this is an experiment, you should absolutely not use this in production. There are no tests and the code quality is not great.

'Simple backend' should not be read as 'limited'. The idea is that you should be able to build a complete but simple (i.e. not complex) backend.

Suitable for systems:

* More reads than writes
* Not expected to have millions of users
* Uptime is not critical

## Important patterns

With SimpleBackend you will be using the event sourcing and CQRS patterns. The promoted way to create or modify data is through an Event which will be inserted
into a state machine.

If you are not familiar with UML state machines you may want to look at https://en.wikipedia.org/wiki/UML_state_machine.

Reads will use materialized views.

The code you write is more functional and declarative than imperative.

## How do I access the database?

In order to simplify things, you don't need to deal with a database. Instead, the data is kept in memory (don't worry, the Events are persisted so the
application can be restarted without loosing data). Benefits:

* All you need is Kotlin, you don't have to be an expert in SQL.
* You don't have to write code to handle errors if the database is unresponsive.
* Less boilerplate: you don't have to convert data to/from the database.
* Better performance since you avoid the network request to the database. Also, with materialized views the data is readily available when requested.

Note that blobs should not be stored in the memory (use e.g. a CDN instead).

Since all data is kept in the application's memory, it must be running on a machine with plenty of RAM (these days you can find cloud instances with up to 24
TiB RAM).

## Scalability

Since the design goal is simplicity, there should only be one instance of SimpleBackend. This means that SimpleBackend can only be scaled vertically. Computers
are pretty fast these days and SimpleBackend should be quite performant so it is likely that one instance will be enough for most systems.

In the future, we may allow more complex configurations that would enable horizontal scalability.

## High availability?

Having only one instance of SimpleBackend means that there is no redundancy. Therefore, it is not a good idea to use SimpleBackend if people's lives are
depending on your application.

That said, it is also true that complex systems are more error-prone than simple systems. Also, uptime can be improved by hosting the application in a Tier IV
datacenter (99.995% uptime).

In the future, we may allow more complex configurations that would enable redundancy.

## Details

### Authentication

The authentication flow is 1) sign in with Google, 2) send the Google-JWT to SimpleBackend, 3) the Google-JWT is verified, 4) the server returns a new JWT that
contains the subject from Google's JWT (i.e. the user ID that you have at Google). Most applications will want to have more information than that they will have
a User model that corresponds to the subject.

### Authorization

You must provide an implementation of the Authorizer interface. One straight forward way is Role Based Access Control where you check if a specific role is
stored on a User model. Just remember to verify updates on the User model so that the user cannot set the roles himself.

