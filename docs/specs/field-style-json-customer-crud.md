# Extend Parameter Contracts to JSON and Complete Customer CRUD

## Problem Statement

Summer MVC currently treats the Parameter contract as a query/form mechanism while JSON request bodies are validated only through Jakarta Bean Validation. This prevents applications from using the same Field/Style declarations, aggregate violations and typed values consistently across query parameters, form values, path variables and JSON bodies. The current behavior also leaves malformed JSON outside the normal invalid-argument path and cannot distinguish an omitted JSON field from an explicitly supplied null value.

Summer Sample does not yet demonstrate a complete, production-shaped CRUD workflow. Its Customer example only creates a customer and reads one by email. The example keeps the SQL template resource location inside the Service, duplicates that value in tests, registers a query Counter inside the Service and increments it for business reads. This mixes configuration, observability, query infrastructure and business orchestration, and does not demonstrate the intended Repository/write-model and SQL template/read-model split.

The reference application therefore does not currently provide a reliable best-practice example for using Field/Style across a complete CRUD lifecycle.

## Solution

Extend the Summer MVC Parameter contract so Field and FieldList can explicitly read values from query/form parameters, JSON bodies and path variables. Preserve existing query/form behavior by default, reuse the existing cached request-body mechanism, retain Jakarta Bean Validation for DTO object constraints and produce the same HTTP 400 Framework error contract for all invalid inputs.

Add presence and nullability semantics so PATCH requests can distinguish an omitted field from an explicit null. Add strict JSON-field enforcement as an opt-in method contract, with Summer Sample enabling it for create and update operations. Continue to export validated and converted values through FieldValues without exposing MVC types to the Service layer.

Expand Summer Sample Customer into a complete CRUD reference. Use Repository persistence for writes, deletion and simple entity access, and a dedicated SQL template query component for dynamic list/read-model behavior. Move the SQL template resource location into an immutable application-wide configuration bean. Remove the Service Counter, MeterRegistry dependency and queryCounter increment entirely.

## User Stories

1. As a Summer MVC application developer, I want Field/Style to validate JSON fields, so that one Parameter contract model can cover every Customer CRUD operation.
2. As a Summer MVC application developer, I want each Field declaration to identify its request source explicitly, so that value precedence is never ambiguous.
3. As an existing Summer MVC adopter, I want current query/form Field declarations to retain their behavior, so that upgrading does not silently change existing endpoints.
4. As an API developer, I want path variables to participate in the Parameter contract, so that resource identifiers use the same validation and error protocol as other inputs.
5. As an API developer, I want JSON nested scalar properties to be addressable with property paths, so that structured request DTOs can still use reusable Styles.
6. As an API developer, I want JSON object arrays to be validated through FieldList, so that list size and item rules are enforced consistently.
7. As an API developer, I want JSON strings, numbers and booleans to preserve their type semantics during Style conversion, so that validation does not depend on accidental string coercion.
8. As an API developer, I want scalar Fields to reject JSON objects and arrays, so that malformed structures are not silently converted into strings.
9. As an API developer, I want required to describe field presence and nullable to describe whether an explicit null is allowed, so that these independent concepts are not conflated.
10. As an API developer, I want FieldValues to report whether a field was present, so that PATCH can distinguish omission from explicit null.
11. As an API developer, I want FieldValues to preserve an explicitly validated null, so that nullable fields can be cleared intentionally.
12. As an API developer, I want Field/Style validation to run before DTO binding without consuming the body permanently, so that normal RequestBody processing continues to work.
13. As an API developer, I want Jakarta Bean Validation to remain available for DTO and cross-field constraints, so that Parameter contracts do not replace object validation.
14. As an API developer, I want all Field violations to be aggregated, so that clients can correct multiple invalid values in one request.
15. As an API client, I want malformed JSON to return HTTP 400 with the normal invalid-argument error shape, so that syntax errors are never reported as server failures.
16. As an API client, I want query, form, path and JSON validation failures to share one error protocol, so that client error handling is transport-source independent.
17. As an API developer, I want strict JSON contracts to reject undeclared fields, so that client spelling errors and unsupported inputs fail immediately.
18. As an existing application developer, I want strict unknown-field rejection to remain opt-in, so that upgrading does not break endpoints that currently tolerate extra properties.
19. As a framework operator, I want JSON Field support to fail clearly at startup when request-body caching is disabled, so that RequestBody failures cannot appear unpredictably at runtime.
20. As a framework maintainer, I want conflicting output names from different request sources to be rejected, so that FieldValues never depends on silent overwrite ordering.
21. As a Customer API client, I want to create a customer and receive the complete created representation, so that generated and normalized values are immediately visible.
22. As a Customer API client, I want to retrieve a customer by immutable email, so that I can address the existing resource consistently.
23. As a Customer API client, I want to list customers with bounded pagination, so that large datasets cannot produce unbounded responses.
24. As a Customer API client, I want a stable default sort, so that repeated pages have deterministic ordering.
25. As a Customer API client, I want to filter customers by allowlisted fields such as active status, so that common read-model queries are supported safely.
26. As a Customer API client, I want sorting to accept only declared fields and directions, so that arbitrary SQL identifiers cannot enter the query template.
27. As a Customer API client, I want an allowlisted output projection, so that list responses can demonstrate Field/Style-driven read-model selection without arbitrary column access.
28. As a Customer API client, I want to partially update a customer, so that omitted properties preserve their current values.
29. As a Customer API client, I want an explicitly nullable property to be clearable with JSON null, so that clearing and omission have different meanings.
30. As a Customer API client, I want immutable email to remain unchanged by updates, so that resource identity stays stable.
31. As a Customer API client, I want to change the customer's display name and active business status, so that the sample demonstrates explicit domain behavior.
32. As a Customer API client, I want to physically delete a customer, so that the sample demonstrates an unambiguous delete operation rather than treating active as a hidden soft-delete flag.
33. As a Customer API client, I want missing resources to use the existing unified not-found behavior, so that CRUD endpoints remain consistent.
34. As a Customer API client, I want duplicate email creation to use the existing business error behavior, so that uniqueness failures remain explicit.
35. As a Summer Sample maintainer, I want the SQL template resource configured once for the application, so that Services and tests do not duplicate resource strings.
36. As a Summer Sample maintainer, I want dynamic reads isolated in a dedicated SQL template query component, so that the Service only orchestrates business operations and transactions.
37. As a Summer Sample maintainer, I want simple entity reads, writes and deletion to use the Repository, so that SQL templates remain focused on complex read models.
38. As a Summer Sample maintainer, I want Customer updates expressed through domain methods, so that the Service does not mutate entity state directly.
39. As a Summer Sample maintainer, I want no Counter or MeterRegistry dependency in Customer Service, so that observability is not mixed into business orchestration.
40. As a framework adopter, I want the sample configuration to use real Summer MVC property names, so that copied configuration is effective.
41. As a framework adopter, I want the Starter guide and README to describe the actual JSON Parameter contract and Customer endpoints, so that documentation matches executable behavior.
42. As a framework architect, I want the earlier JSON-only Bean Validation decision explicitly superseded, so that the ADR history remains honest.
43. As an implementation agent, I want a red test before each behavior is implemented, so that the change demonstrates the intended regression protection.
44. As a maintainer, I want module and repository-wide regression suites to report their real test counts, failures and skips, so that successful delivery is evidence-based.

## Implementation Decisions

- Summer MVC remains the owner of unified HTTP parameter, validation, error and response semantics.
- The Parameter contract expands from query/form and dynamic parameters to query/form parameters, JSON bodies and path variables.
- Scalar Field sources are inferred automatically from Query/form, JSON and PATH input; the public Field source setting is removed. FieldList sources are inferred from indexed Query/form parameters or JSON arrays; PATH lists are unsupported.
- JSON Field paths use Spring-style property paths. Root and nested scalar values are supported.
- FieldList supports a one-level JSON array of objects, including nested scalar paths inside each declared item.
- Primitive arrays, nested arrays and recursive object-array declarations are not added in this change.
- Field and FieldList continue to export validated, typed values into FieldValues.
- Field and FieldList use required=true and nullable=false by default. Required controls whether a value must be present; nullable controls whether a present JSON value may be null. FieldList items are Field[] and collection size is configured independently with minSize/maxSize.
- FieldValues gains a presence operation and changes its immutable storage so an explicitly validated null can be retained.
- Existing FieldValues retrieval behavior remains compatible for non-null values.
- FieldContract is a marker for an explicit contract. Unknown Query/form, JSON and PATH fields are rejected consistently; there is no leniency switch.
- A handler must not declare multiple sources that export to the same FieldValues name. Such a contract is a configuration error rather than a runtime precedence rule.
- JSON parsing occurs in the existing Field validation interceptor before RequestBody argument conversion.
- JSON parsing reads only from the existing cached-body request wrapper. The same bytes remain readable by Jackson for DTO binding.
- JSON Parameter contracts require request-body caching. An application configuration that enables JSON Field support while disabling caching fails during startup with a clear configuration message.
- The existing ObjectMapper managed by Spring is reused; the framework does not construct a private mapper.
- JSON Field validation and DTO Jakarta Bean Validation coexist. Field/Style owns the transport-field whitelist, reusable Style validation, conversion and aggregate Field violations. Bean Validation owns DTO and cross-field object constraints.
- Controllers may receive a validated RequestBody DTO or FieldValues and pass ordinary Java values into Services; Services do not depend on MVC types or duplicate one-use command wrappers.
- Malformed JSON is mapped to HTTP 400 for both Field-enabled and ordinary DTO endpoints. Field, Bean Validation and unreadable-body failures use the unified invalid-argument response contract.
- Existing query/form Parameter contract behavior, annotation defaults and supported controller signatures remain compatible.
- Existing interceptor construction remains source-compatible while automatic configuration injects the application ObjectMapper through the extended construction path.
- Summer Sample exposes Customer list, detail, create, partial update and delete operations. Email remains the immutable resource identifier.
- Create returns the complete Customer detail representation. Delete uses the established no-content success response. Missing resources use the existing not-found protocol.
- PATCH treats an omitted field as unchanged and an explicit null as a request to clear a nullable property. Non-nullable nulls produce Field violations.
- Customer active is a business status and list filter, not a soft-delete marker. Delete remains physical.
- Customer exposes explicit domain behavior for allowed updates instead of allowing the Service to manipulate fields directly.
- Repository persistence is used for writes, deletion and simple entity access.
- A dedicated Customer query component owns JpaSqlTemplate use, dynamic list filtering, sorting, pagination and projection.
- The Customer Service owns business orchestration and transaction boundaries. It does not depend on JpaSqlTemplate, SQL resource paths, Counter or MeterRegistry.
- No query counter replaces the removed Service metric. Future metrics belong in infrastructure-level observability components.
- The SQL template resource is configured through an immutable Sample configuration bean using the application property summer.sample.data.sql-resource.
- Tests obtain the SQL template resource through the same configuration bean and do not duplicate its string value.
- Dynamic filter, sort and projection inputs are allowlisted through Parameter contracts. Client input cannot supply arbitrary SQL identifiers or fragments.
- Read operations use read-only transactions; create, update and delete use write transactions. Controllers do not open transactions.
- The ineffective summer.web configuration namespace in Summer Sample is replaced with the real summer.mvc properties.
- A new ADR supersedes the earlier decision that limited Parameter contracts to non-JSON parameters. The previous ADR remains in history and is not silently rewritten.
- The Starter guide, README, domain vocabulary where necessary and executable sample are updated together.
- The public JpaSqlTemplate contract is not changed by this work.

## Testing Decisions

- Tests exercise externally visible contracts and architectural boundaries rather than duplicating internal implementation steps.
- Work begins with a valid failing test for each new behavior before implementation.
- The primary acceptance seam is Summer Sample HTTP end-to-end testing through the real Spring application, Controller, Parameter contract, DTO binding, Service, Repository or SQL template path and database.
- End-to-end coverage includes create, duplicate create, detail, missing detail, bounded list pagination, stable sorting, allowlisted filtering, allowlisted projection, partial update, omission semantics, explicit null semantics, immutable email, physical delete and missing delete/update behavior.
- End-to-end coverage verifies JSON unknown-field rejection, malformed JSON, invalid path values, invalid query values and aggregate Field violations through the unified response.
- Summer MVC integration tests exercise the public Parameter contract through MVC request handling for query/form, path and JSON sources.
- Summer MVC integration coverage includes JSON strings, numbers, booleans, nested paths, missing values, explicit null, invalid node types, aggregate failures and prevention of partial value export.
- FieldList integration coverage includes JSON object arrays, actual array-size enforcement, non-empty collection rules, indexed Query/form input, contiguous indexes, child Field rules, nested item scalar paths and local maximum-size overrides.
- A combined MVC test proves that the interceptor can read JSON before the same request is deserialized into a valid RequestBody DTO.
- A compatibility test proves ordinary RequestBody plus Jakarta Bean Validation endpoints continue to work without Field annotations.
- Malformed JSON tests prove both Field-enabled and ordinary DTO endpoints return HTTP 400 rather than a generic server error.
- Existing query/form tests remain regression coverage and must continue to pass unchanged.
- Automatic-configuration tests verify ObjectMapper injection, existing constructor compatibility, filter/interceptor ordering and the startup failure when JSON Field support is combined with disabled request-body caching.
- An architecture test enforces that Customer Service does not depend on Counter, MeterRegistry or JpaSqlTemplate.
- Existing SummerEndToEndTest, cached-body request tests, automatic-configuration tests, Modulith verification and persistence tests are prior art to extend rather than replace.
- Verification runs Summer MVC tests, Summer MVC automatic-configuration tests, the complete Summer Sample test suite and the complete repository test suite as separate gates.
- Final reporting includes actual tests, failures, errors and skipped counts for each gate. A build success is not substituted for test-execution evidence.

## Out of Scope

- Reintroducing JSON Schema validation.
- Replacing Jakarta Bean Validation for DTO or object-level constraints.
- Supporting headers, cookies or multipart file contents as Field sources.
- Supporting primitive JSON arrays, nested arrays or recursively nested FieldList structures.
- Treating active as a soft-delete marker.
- Allowing Customer email to change after creation.
- Adding a new Service-level or business-level query metric to replace queryCounter.
- Changing the public JpaSqlTemplate calling contract.
- Turning SQL templates into an ORM or write-model replacement.
- Restoring a global mutable request context or static application-context access.
- Committing, pushing or creating a pull request as part of this specification task.

## Further Notes

- This solution intentionally contradicts the current ADR that assigns JSON bodies exclusively to Bean Validation. Implementation must add a superseding ADR and preserve the prior decision as historical context.
- The design uses the project glossary terms Summer MVC, Parameter contract, Style, Summer Sample and SQL template.
- The Customer example is a reference application contract, not a new Summer 4 product capability boundary.
- The request-body cache is already capable of repeated reads; the implementation should extend the existing seam rather than introduce another buffering mechanism.
- The complete design was settled through the preceding batch design interview. No functional decisions remain open in this specification.
