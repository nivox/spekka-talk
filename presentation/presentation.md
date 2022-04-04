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
* possibly generate some output...
* ...read by other streaming microservices

---
#### Desired properties

1. Complete (i.e. process all data)
2. Resilient to failures
3. Scalable with data volume

---
#### Typical architecture 
![bg fit right](synchronous.png)

* Durable message queue (Kafka)
* Storage layer
* API layer

---
#### The problem with state

* Exactly once is a lie
* At least once
* Idempotency or input deduplication
* Effectively once

