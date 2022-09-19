---
marp: true
theme: uncover
class: invert
paginate: true
_paginate: false
footer: Andrea Zito (@nivox)

---
# Speck Flavored Streaming Micro-Services with Akka

---
<!-- _class: invert -->
#### Who the heck are you?
- ![w:64](images/avatar_scalalove.png) 
- Andrea Zito
- @nivox
- ~~Senior Software Engineer @ thinkin.io~~
- Scala Developer @ commercetools
- [https://nivox.github.io](https://nivox.github.io)


---
<!-- header: Spekka -->

#### Speck Flavored What!?

* Spekka ([spekka.github.io](https://spekka.github.io))
* Streaming Productivity Enhancements for Akka
* Retrofitted acronym to make it sound like *speck* (🥓)

---
<!-- header: Introduction -->

#### What is a Streaming Micro-Service?

* reads data from a streaming source
* performs some computation
* expose API to interact with it
* possibly generates some output...
* ...read by other streaming micro-services

---
#### Desired properties

* Resilient (recover failures)
* Scalable (adapt to data amount)
* Complete & correct

---
#### Completeness & correctness

* Process all the data...
* ... but not more than once

---

#### Exactly once semantic

* Exactly once is a lie 🍰
* At least once
* Idempotency / input de-duplication
* Effectively once

---
#### Typical architecture 
![bg fit right](images/StreamingMicroservices.png)

- Durable message queue (Kafka)
- Storage layer
- API layer

---
#### Interaction pattern

1. read data + offset from Kafka
1. process the data (keeping relationship to offset)
1. produce outputs (side-effect!)
1. persist state (if any)
1. commit the offset

---
<!-- header: Akka Stream -->

# Akka Stream

* enables creation of (complex) computation pipelines
* providing out of the box back-pressure

---
<!-- header: Akka Stream -->

# Akka Streams

![w:640](images/AkkaFlow.png)

---
<!-- header: One-to-one flows -->

## problem: one-to-one flows

* Akka Stream `Flow` are too powerful:
  * can drop elements
  * can duplicate elements
  * can produce elements out of thin air
  * do not model the context associated to elements

---
## problem: one-to-one flows

* Akka Stream provides `FlowWithContext`:
  * solves the context modelling problem
  * but still allows to filter/duplicate elements
  * restrict usage of reordering operators (statefulMapConcat, groupBy)

---
<!-- header: FlowWithExtendedContext -->
### `FlowWithExtendedContext`

* Spekka solution to one-to-one flows:
  * strictly guarantees one-to-one semantic...
  * ... while allowing reordering operators

---
##### The Big Superheroes Stream 🦸
```scala
sealed trait SuperheroUniverse
case object Marvel extends SuperheroUniverse
case object DC extends SuperheroUniverse

case class Movie(
    title: String,
    actors: List[String],
    universe: SuperheroUniverse,
    durationMin: Int
)
```

---
### Objective

Build a program computing the rolling total duration of movies in the stream

---

```scala
val moviesDurationFlow = FlowWithExtendedContext[Movie, Ctx]
  .statefulMap(() => {
    var totalDuration: Int = 0

    (movie) =>
      totalDuration += movie.durationMin
      totalDuration
  })
```

---
<style scoped>
{
  font-size: 36px
}
</style>
```
ctx=0 movie=Man of Steel        143
ctx=1 movie=Batman vs Superman  294
ctx=2 movie=Green Lantern       417
ctx=3 movie=Justice League      537
ctx=4 movie=Ironman             663
ctx=5 movie=Thor                778
ctx=6 movie=The Avengers        921
ctx=7 movie=Deadpool            1029
```

---
### Objective rev. 1

Build a program computing the rolling total duration of movies in the stream **grouped by universe**

---
```scala
val moviesDurationByUniverseFlow =
    Partition
      .treeBuilder[Movie, Ctx]
      .dynamicAuto(_.universe)
      .build { case universe :@: KNil =>
        moviesDurationFlow.map(universe -> _)
      }
```

---
<style scoped>
{
  font-size: 36px
}
</style>
```
ctx=0 movie=Man of Steel        (DC,143)
ctx=1 movie=Batman vs Superman  (DC,294)
ctx=2 movie=Green Lantern       (DC,417)
ctx=3 movie=Justice League      (DC,537)
ctx=4 movie=Ironman             (Marvel,126)
ctx=5 movie=Thor                (Marvel,241)
ctx=6 movie=The Avengers        (Marvel,384)
ctx=7 movie=Deadpool            (Marvel,492)
```

---
### Objective rev. 2

Build a program computing the rolling total duration of movies in the stream **grouped by actor and universe**

---
```scala
  Partition
    .treeBuilder[Movie, Ctx]
    .dynamicAutoMulticast[String]( 
      { case (movie, _) => movie.actors.toSet }
    )
    .dynamicAuto(_.universe)
    .build { case universe :@: actor :@: KNil =>
      moviesDurationFlow.map((actor, universe) -> _)
    }
```

---
<style scoped>
{
  font-size: 36px
}
</style>
```
ctx=0 movie=Man of Steel        List(((Hanry Cavil,DC),143))

ctx=1 movie=Batman vs Superman  List(((Ben Affleck,DC),151), 
                                     ((Hanry Cavil,DC),294))

ctx=2 movie=Green Lantern       List(((Rayan Reynolds,DC),123))

ctx=3 movie=Justice League      List(((Gal Gadot,DC),120), 
                                     ((Hanry Cavil,DC),414), 
                                     ((Ben Affleck,DC),271))

ctx=4 movie=Ironman             List(((Robert Downey Jr,Marvel),126))

ctx=5 movie=Thor                List(((Chris Hamsworth,Marvel),115))

ctx=6 movie=The Avengers        List(((Chris Hamsworth,Marvel),258), 
                                     ((Robert Downey Jr,Marvel),269))

ctx=7 movie=Deadpool            List(((Rayan Reynolds,Marvel),108))
```

---
<style scoped>
{
  font-size: 36px
}
</style>
```scala
  val flow1: FlowWithExtendedContext[In, A, Ctx, M1] = ???
  val flow2: FlowWithExtendedContext[In, immutable.Iterable[B], Ctx, M2] = ???
  val flow3: FlowWithExtendedContext[B, C, Ctx, M3] = ???

  flow1.ordered() // Ensures ordering of outputs (with backpressure)

  FlowWithExtendedContext.zip(flow1, flow2) // Zips multiple flows
  // => FlowWithExtendedContext[In, (A, immutable.Iterable[B]), Ctx, (M1, M2)]

  flow2.viaMultiplexed(flow3) // Applies flow3 to flow2 output's elements
  // => FlowWithExtendedContext[In,immutable.Iterable[C],Ctx,M2]

  // Interacts nicely with standard Akka Streams
  val source: Source[(In, Ctx), NotUsed] = ???
  val source1: Source[(A, Ctx), NotUsed] = source
    .map { case (in, ctx) => in -> ExtendedContext(ctx) }
    .via(flow1)
    .map { case (in, ectx) => in -> ectx.innerContext }
 ```

---
![w:640](images/FlowWithExtendedContext.png)

* solves the one-to-one problem...
* ... but still doesn't facilitate external interaction

---
<!-- header: Stateful flows -->

## problem: stateful flows interaction

* `Flow` and `FlowWithExtendedContext`:
  * allows to define stateful computation
  * do NOT provide easy way to interact with state

---
#### Spekka's model for stateful flows
* **logic**: defines what the flow does
  * event-based or durable-state
  * sync or async variants
* **backend**: defines how the logic is actually run
* **registry**: handles stateful flow lifecycle

---

Lets revisit our program using Spekka's stateful flows 

---
#### logic definition
<style scoped>
{
  font-size: 28px
}
</style>
```scala
sealed trait Command
object Command {
  case class GetCurrentDuration(replyTo: ActorRef[StatusReply[Int]]) extends Command
}

val moviesDurationLogic = 
  StatefulFlowLogic.DurableState[Int, Movie, Int, Command](
    () => 0,
    (state, in) => {
      val updatedDuration = state + in.durationMin
      StatefulFlowLogic.DurableState.ProcessingResult(
        updatedDuration, 
        updatedDuration
      )
    },
    (state, command) =>
      command match {
        case Command.GetCurrentDuration(replyTo) =>
          replyTo.tell(StatusReply.success(state))
          StatefulFlowLogic.DurableState.ProcessingResult(state)
      }
)
```

---
#### backend selection
```scala
val moviesDurationProps = 
  moviesDurationLogic.propsForBackend(
    InMemoryStatefulFlowBackend.DurableState()
  )
```

---
#### registration
```scala
val registry = StatefulFlowRegistry(30.seconds)

val moviesDurationByActorUniverseBuilder = 
  registry.registerStatefulFlow(
    "by-actor-universe", 
    moviesDurationProps
  )
```

---
#### usage
```scala
  Partition
    .treeBuilder[Movie, Ctx]
    .dynamicAutoMulticast[String](
      { case (movie, _) => movie.actors.toSet }
    )
    .dynamicAuto(_.universe)
    .build { case universe :@: actor :@: KNil =>
      val entityId = s"${actor}-${universe}"
      moviesDurationByActorUniverseBuilder
        .flowWithExtendedContext[Ctx](entityId)
        .map(_.map((actor, universe) -> _))
    }
    .map(_.flatten)
```

---

#### interacting

```scala
val control = moviesDurationByActorUniverseBuilder.lazyControl

for {
  duration <- control.commandWithResult[Int](
    s"Henry Cavill-${DC}", 
    StatefulFlow.Command.GetCurrentDuration
  )
} yield println(s"*** Henry Cavill DC duration: ${duration}")
```

---
<!-- header: Stateful flows -->

## problem: side effect ordering

Interaction with the outside world => side effects

* emitting to Kafka in response to stream inputs
* doing *stuff* in response to commands

---
#### side effects

* processing functions may specify side-effects:
  * before the state is modified
  * after the state is modified

```scala
StatefulFlowLogic.DurableState.ProcessingResult(
  updatedState, 
  outputs
).withBeforeUpdateSideEffect(() => ??? /* at-least-once semantic */)
 .withAfterUpdateSideEffect(() => ??? /* at-most-once semantic */)  
```

---
<!-- header: Stateful flow persistence -->

## problem: state persistence

The state is still transient, we loose everything if our program restart!

---
#### Spekka Stateful Akka Persistence Backend
* persisted implementation of `StatefulFlowBackend` for:
  * Event based flows (`EventSourcedBehavior`)
  * Durable state flows (`DurableStateBehavor`)


---
<style scoped>
{
  font-size: 28px
}
</style>
```scala
val eventLogic: StatefulFlowLogic.EventBased[State, Ev, In, Command] = ???
eventLogic.propsForBackend(
  AkkaPersistenceStatefulFlowBackend.EventBased(
      AkkaPersistenceStatefulFlowBackend.EventBased.PersistencePlugin.CassandraStoragePlugin
    )
    .withEventAdapter(???)
    .withSnapshotAdapter(???)
    .withEventsPartitions(???) // for use with Akka Projection
)

val durableLogic: StatefulFlowLogic.DurableState[State, In, Out, Command] = ???
durableLogic.propsForBackend(
  AkkaPersistenceStatefulFlowBackend.DurableState(
    AkkaPersistenceStatefulFlowBackend.DurableState.PersistencePlugin.JdbcStoragePlugin
  ).withSnapshotAdapter(???)
)
```

---
<!-- header: Sharded flows -->
## problem: scalable flows

* our flow cannot scale!
* the more movie with process... the more actor we learn about...
* at some point we will run out of memory! 

---
#### Spekka Stateful Sharding

* integrates with Akka Cluster Sharding
* transparently distributing stateful flow on a cluster

---
```scala
val registry = StatefulFlowRegistry(30.seconds)

val clusterSharding = ClusterSharding(system.toTyped)

val shardedRegistry =
  ShardedStatefulFlowRegistry(registry, clusterSharding, 30.seconds)

val moviesDurationByActorUniverseBuilder = 
  shardedRegistry.registerStatefulFlow(
    "by-actor-universe", 
    moviesDurationProps
  )
```

---
<!-- header: "" -->
# Conclusion

Spekka helps you build:

* effectively once
* stateful
* sharded

---
# Conclusion

Spekka helps you building:

- effectively once
- stateful
- sharded

### Streaming Micro-Services

---
##### Spekka micro-site: 
[https://spekka.github.io](https://spekka.github.io)

## Sponsors
Developement of this library has been sponsored by
![](images/thinkin.png)