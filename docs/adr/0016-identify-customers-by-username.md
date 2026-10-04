# Identify Customers by username

Customer resources will use an immutable `username` as their stable business identifier, while retaining `email` as a separate contact attribute and `customerId` as an internal database identifier. Customer detail, update, and delete APIs and the Summer UI detail route will therefore address Customers by username rather than email, accepting the coordinated breaking change because it removes the existing conflation of resource identity and contact information.

## Considered Options

- Keep email as the resource identifier: rejected because an email is contact information and may change independently of Customer identity.
- Rename email to username and discard email: rejected because it loses the distinct contact-information concept.
- Add immutable username and retain email: accepted because it separates identity, contact information, and internal persistence identity.


## Compatibility boundary

The existing `/api/customers/{identifier}` route shape is retained, but its path-variable contract changes completely from email to username. Controllers, services, repositories, OpenAPI descriptions, tests, and documentation will use `username` and perform only username lookup. Summer Sample will not add fallback email lookup, a `by-email` resource route, or redirects from email-based paths. A legacy request containing an email in the resource path fails username validation with `400 SUMMER_INVALID_ARGUMENT` rather than being interpreted silently under the old contract. Email remains a mutable contact and sortable list field, not a resource locator.


## Sample schema lifecycle

Summer Sample uses MySQL as its persistent runtime database and Flyway as the schema owner. The Customer baseline migration creates required unique username and email columns, a required display name, a required active state, and the audit timestamps without dropping an existing `summer_customer` table. Hibernate schema generation is disabled for the runtime profile; H2 remains available only in the test profile. Customer seed data is inserted through the application runner and therefore survives restarts without being recreated destructively. The MySQL datasource URL, username, and password are supplied by controlled runtime configuration, with no credential fallback in the application profile.
