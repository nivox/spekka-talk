---
marp: true
theme: uncover
class: invert
paginate: true
_paginate: false
footer: Andrea Zito

---
# Building Streaming Microservices with Akka
---
<!-- header: Introduction -->

#### What is a streaming microservice?

* reads data from a streaming source
* performs some computation
* expose API to interact with it
* possibly generates some output...
* ...read by other streaming microservices

---
#### Desired properties

1. Resilient (recover failures)
1. Scalable (adapt to the data amount)
1. Correct (duh 🤦)

---
#### Correctness

* Process all the data...
* ... but not more than once

---

#### Exactly once semantic

* Exactly once is a lie 🍰
* At least once
* Idempotency / input deduplication
* Effectively once

---
#### Typical architecture 
![bg fit right](synchronous.png)

* Durable message queue (Kafka)
* Storage layer
* API layer

---
<!--header: Scenario -->

#### Use case scenario

* Centralised people counter system
* Each entrance has a dedicated sensor
* Edge service collect sensor's data and push to cloud
* Multiple deployment

```json
{ 
    "timestamp": 1640995200000, 
    "deploymentId": "Carnegie Hall", 
    "entranceId": 0, 
    "counter": 1
}
```

---
<!-- header: Implementation: Plain Akka Streams -->

# Let's get coding

#### Plain Akka Streams
---
<!-- header: Implementation: Plain Akka Streams -->

# Considerations

* Interacting (properly) with the stream is not easy...
* ... impossible when using dynamic partitioning
* Built-in partitioning does not guarantee at-least-once
* Common tasks often require a lot of non trivial code
* No out of the box suuport for state persistence

#### Can we do better?

---
<!-- header: Implementation: Spekka Context -->

# Spekka Context

* Provides one-to-one context propagation...
* ... while supporting advanced partitioning

---
<!-- header: Implementation: Spekka Context -->

# Let's get coding

#### Spekka Context

---
<!-- header: Implementation: Spekka Context -->

# Considerations

* Interacting (properly) with the stream is still hard...
* ... but possible also when partitioning
* Stream order is guaranteed also when partitioning
* Still no support for state persistence

#### Can we do better?

---
<!-- header: Implementation: Spekka Stateful -->

# Spekka Stateful

* Models stateful flows by:
    * Logic: how they behave
    * Backend: how they manage state

---
<!-- header: Implementation: Spekka Stateful -->

# Let's get coding

#### Spekka Stateful

---
<!-- header: Implementation: Spekka Stateful -->

# Considerations

* Stream interactions are now first class...
* ... and much easier
* Out of the box support for side effects

#### Can we do better?

---
<!-- header: Implementation: Spekka Stateful Akka Persistence -->

# Spekka Stateful Akka Persistence

* Spekka Stateful Backend based on Akka Persistence
* Allows to persist state to:
    * Event based: cassandra, sql
    * Durable state: sql

---
<!-- header: Implementation: Spekka Stateful Akka Persistence -->

# Let's get coding

#### Spekka Stateful Akka Persistence

---
<!-- header: Implementation: Spekka Stateful Akka Persistence -->

# Considerations

* Changing storage layer has never been so 🥱

#### Can we do better?

---
<!-- header: Implementation: Spekka Stateful Sharding -->

# Spekka Stateful Sharding

* Extends stateful flows making them clustered
* Based on Akka Cluster Sharding

---
<!-- header: Implementation: Spekka Stateful Sharding -->

# Let's get coding

#### Spekka Stateful Sharding

---
<!-- header: Implementation: Spekka Stateful Sharding -->

# Considerations

* Well that was anticlimactic!
* A couple of lines of code...
* Some additional configuration
* And our microservice is now clustered! 🤯

#### Can we do better?

---
<!-- header: "" -->

![](not_today.gif)

---
# Conclusion

#### Spekka makes it easier to build

* effectively once
* stateful
* sharded

#### streaming microservices


