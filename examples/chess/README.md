# Chess example

## Prerequisites

Build the JAR of the parent project.

## About the move history

In order to show how events can be read from the database, the move history is acquired with the getEventsForModelId function which will read events from the
database. However, it would be better to store the history in GameProperties.
