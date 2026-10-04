# Version authorization changes through an outbox and Redis Stream

Authorization role, capability, menu and data-scope mutations append a MySQL
outbox row in the same application transaction. A scheduler publishes rows to
`summer:authorization-change-events` with at-least-once delivery and bounded
retry/dead-letter state. Every node consumes through its own Redis Stream
consumer group and evicts its local authorization cache projection before
applying a shared receipt idempotently.

The event carries the enterprise boundary, resource identity, event type,
monotonic resource version and request correlation identifier. A node that
missed a stream delivery reloads its persistent authorization projection at the
HTTP decision seam, so a Redis restart or lost notification cannot preserve an
indefinitely stale permission decision. Audit rows are written before the
outbox event is enqueued, preserving the mutation-to-event order.
