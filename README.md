# A Simple Backend

Simple Backend is a framework for building backend in Kotlin while avoiding as much complexity as possible.

It is also an experiment where we test the hypothesis "It is a good idea to structure the whole system around state machines".

## Overview

Status: As this is an experiment, you should absolutely not use this in production. There are no tests and the code quality is not great.

'Simple backend' should not be read as 'limited'. The idea is that you should be able to build a complete but simple (i.e. not complex) backend.

Suitable for systems:

* More reads than writes
* Auditing is required
* The system has complicated business logic, and you don't want to reimplement it on the frontend
* Low latency is important
* Not expected to have millions of users
* Uptime is not super important

### Important patterns

With SimpleBackend you will be using the event sourcing and CQRS patterns. The promoted way to create or modify data is through an Event which will be inserted
into a state machine.

If you are not familiar with UML state machines you may want to look at https://en.wikipedia.org/wiki/UML_state_machine.

The code you write is more functional and declarative than imperative. You will declare state machines using functions that you provide. The functions must not
have any side effects (but they don't have to be pure since they may read the current state from views).

### How do I access the database?

In order to simplify things, you don't need to deal with a database. Instead, the data is kept in memory (don't worry, the Events are persisted so the
application can be restarted without loosing data). Benefits:

* All you need is Kotlin, you don't have to know any SQL.
* You don't have to write code to handle errors if the database is unresponsive.
* Less boilerplate: you don't have to convert data to/from the database.
* Lower latency since you avoid the network request to the database.

Note that blobs should not be stored in the memory (use e.g. a CDN instead).

Since all data is kept in the application's memory, it must be running on a machine with plenty of RAM (these days you can find cloud instances with up to 24
TiB RAM).

### Caching?

As the data resides in memory, there is no need for caches. SimpleBackend is likely going to respond to a query within 1 ms.

### Scalability

Since the design goal is simplicity, there should only be one instance of SimpleBackend. This means that SimpleBackend can only be scaled vertically. Computers
are pretty fast these days and SimpleBackend should be quite performant, so it is likely that one instance will be enough for most systems. If this is not
enough, you could consider sharding (e.g. one instance per customer).

In the future, we may allow more complex configurations that would enable horizontal scalability.

### High availability?

Having only one instance of SimpleBackend means that there is no redundancy. Therefore, it is not a good idea to use SimpleBackend if people's lives are
depending on your application.

That said, it is also true that complex systems are more error-prone than simple systems. Also, uptime can be improved by hosting the application in a Tier IV
datacenter (99.995% uptime).

In the future, we may allow more complex configurations that would enable redundancy.

### GraphQL

To modify the data on the server, you will use a GraphQL mutation ("createEvent") which is built in.

To query the backend, you GraphQL queries are used (you will build these yourself).

At the moment, gRPC is used to manage subscribe to events (this will be handled with GraphQL in the future).

### Authentication

Currently, only Google Sign-In is supported. The client should acquire a JWT from Google and that JWT should be set in the 'Authorization' header
(as "Bearer ..."). In your application, you can access a UserIdentity object which will contain "subject" from the JWT. Most applications will want to have more
information about the user than that, so you will likely want to have a User model that corresponds to the subject.

### Authorization

SimpleBackend includes a rule engine which makes it easy to implement an attribute-based access control (ABAC). By default, both reads and events are denied, so
you need to add rules that explicitly allows users to do stuff.

ABAC is quite flexible and can easily be used to only check for roles if you prefer role-based access control (RBAC).

It is recommended that your views should not return the data directly but wrap it in an ReadModelAuthorizer object. This way you cannot forget to authorize
queries. See the examples for details how it can be used.

## What if I need more than SimpleBackend can provide?

It is possible to provide custom GraphQL queries and modifications when configuring SimpleBackend, giving you full control.
