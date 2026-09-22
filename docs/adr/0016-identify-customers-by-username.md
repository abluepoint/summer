# Identify Customers by username

Customer resources will use an immutable `username` as their stable business identifier, while retaining `email` as a separate contact attribute and `customerId` as an internal database identifier. Customer detail, update, and delete APIs and the Summer UI detail route will therefore address Customers by username rather than email, accepting the coordinated breaking change because it removes the existing conflation of resource identity and contact information.

## Considered Options

- Keep email as the resource identifier: rejected because an email is contact information and may change independently of Customer identity.
- Rename email to username and discard email: rejected because it loses the distinct contact-information concept.
- Add immutable username and retain email: accepted because it separates identity, contact information, and internal persistence identity.


## Compatibility boundary

The existing `/api/customers/{identifier}` route shape is retained, but its path-variable contract changes completely from email to username. Controllers, services, repositories, OpenAPI descriptions, tests, and documentation will use `username` and perform only username lookup. Summer Sample will not add fallback email lookup, a `by-email` resource route, or redirects from email-based paths. A legacy request containing an email in the resource path fails username validation with `400 SUMMER_INVALID_ARGUMENT` rather than being interpreted silently under the old contract. Email remains a mutable contact and sortable list field, not a resource locator.


## Sample schema lifecycle

Summer Sample keeps its existing Hibernate `ddl-auto: create-drop` lifecycle for this change. The recreated Customer table gains required unique username and email columns, a required display name, and a required active state; seed data and persistence tests are updated accordingly. Because the Sample already discards its schema between runs, this scope does not add Flyway, Liquibase, or a legacy email-to-username backfill. Applications using persistent databases must supply their own explicit production migration rather than treating the Sample's schema generation as a migration strategy.
