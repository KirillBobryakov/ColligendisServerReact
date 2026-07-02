# Neo4j data model (Colligendis server)

This document describes how **this application** structures data in Neo4j: node labels, relationship types, common properties, and lifecycle conventions. It is derived from the Java domain types under `com.colligendis.server.database` and the write/read logic in `AbstractService`.

The stack uses the **Neo4j Java Driver** with hand-written Cypher (see `Neo4jConfig`, `AbstractService`). It does **not** use Spring Data Neo4j `@Node` / `@Relationship` mapping on entities.

## Database and driver

- **Database name**: `spring.neo4j.database` (defaults to `neo4j` if unset); sessions are opened with that name in `AbstractService`.
- **Connection**: `Neo4jConfig` builds a pooled `Driver` from `spring.neo4j.uri` and basic auth.

## Node identity and labels

- Every persisted domain object extends `AbstractNode` and is stored as a node with a **single primary label** matching the `LABEL` constant on its Java type (for example `NTYPE`, `ISSUER`).
- **Stable identifier**: `uuid` (string). New nodes get `uuid: randomUUID()` on create (`AbstractService#createNode`).
- **Business / external keys** (examples): Numista `nid` on several types, `numistaCode` on `Issuer` / `Subject` / `Country`, catalogue `code` / `number`, etc. These are **ordinary properties**, not Neo4j internal ids.

## Common node properties (`AbstractNode`)

All domain nodes inherit these fields; they are included in `getPropertiesMap()` when non-null (strings also require non-blank text):

| Property     | Meaning |
|-------------|---------|
| `uuid`      | Primary key for application Cypher |
| `createdAt` | Set on create (`datetime`, timezone `+03:00`) |
| `createdBy` | User `uuid` who created (or empty string) |
| `updatedAt` | Set when the node is superseded by a property update (versioning) |
| `updatedBy` | User `uuid` for the update |
| `deletedAt` | Set on soft delete |
| `deletedBy` | User `uuid` for soft delete |

`ColligendisUser` extends `AbstractUser`, which adds `username` and `password` on top of `AbstractNode`.

## Name and text search (`normalized*` properties)

Several types persist both a human-facing string and a **normalized** twin used for stable matching (for example `name` / `normalizedName`, `title` / `normalizedTitle`, `fullName` / `normalizedFullName`, `lettering` / `normalizedLettering`). Values are produced in Java via `UnicodeNormalizer` when setters run, then stored on the node like any other property.

**When adding Cypher or services that search or filter “by name” (or similar free text):**

- Prefer conditions on the **`normalized*` property** (not the raw display field), so search matches the same canonical form as ingestion.
- Apply the **same normalization** to user input or external search terms before binding query parameters (use `UnicodeNormalizer` in Java, not ad-hoc lowercasing unless you intentionally diverge).

**Batch repair:** `com.colligendis.server.util.NormalizedNeo4jPropertyUtil#syncNormalizedProperties` scans live nodes (skips `*_DELETED` / `*_VERSIONED`) that have either a `normalized*` property or a known source field (`name`, `title`, `fullName`, `lettering`, `comment` — see `SOURCE_PROPERTIES_WITH_NORMALIZED_TWIN`). It derives paired keys, recomputes with `UnicodeNormalizer`, and `SET`/`REMOVE` when the stored value is out of date.

## Versioning and soft delete (labels)

### Property updates (versioning)

When node **properties** change in a meaningful way, `AbstractService#updateNodeProperties` may:

1. Clone the node with **APOC** (`apoc.refactor.cloneNodes`), copy changed properties onto the clone, assign a **new** `uuid` and `updatedAt` / `updatedBy`.
2. Link **`(newNode)-[:PREVIOUS_VERSION]->(oldNode)`**.
3. Remove the live label(s) from the old node and add **`LABEL + "_VERSIONED"`** (for example `NTYPE_VERSIONED`).

**Requirement**: APOC must be available in the Neo4j instance for this update path.

### Soft delete (nodes)

`AbstractService#deleteNode` does **not** remove the node. It:

1. Removes the original label(s).
2. Adds **`LABEL + "_DELETED"`** (for example `NTYPE_DELETED`).
3. Sets `deletedAt` / `deletedBy`.

Queries that should ignore tombstones and old versions should filter labels, for example:

```cypher
MATCH (n:NTYPE)
WHERE NOT any(l IN labels(n) WHERE l ENDS WITH '_DELETED' OR l ENDS WITH '_VERSIONED')
RETURN n
```

(Adjust for your exact read patterns.)

## Relationships

### Creation and identity

- Relationships are created/merged via `AbstractService` helpers (for example `createUniqueTargetedRelationship(source, target, relationshipType)`).
- Pattern: **`(sourceLabel {uuid: $sourceUuid})-[r:REL_TYPE]->(targetLabel {uuid: $targetUuid})`**.
- On **MERGE** of a new relationship, Neo4j stores at least `createdAt` and `createdBy` on the relationship (see `AbstractService` Cypher).
- If the same relationship type is reused toward a **different** target, the implementation may move the old relationship to a type suffixed with **`_DELETED`** and copy its properties before creating the new active edge.

### Direction convention in this doc

Unless stated otherwise, an entry **`A --REL--> B`** means the stored pattern is **`(A)-[:REL]->(B)`**, matching `createUniqueTargetedRelationship(A, B, "REL")` as used in services.

---

## Node labels (catalog)

| Label | Java type |
|-------|-----------|
| `NTYPE` | `NType` |
| `NTYPE_PART` | `NTypePart` |
| `VARIANT` | `Variant` |
| `COLLECTIBLE_TYPE` | `CollectibleType` |
| `ISSUER` | `Issuer` |
| `ISSUING_ENTITY` | `IssuingEntity` |
| `COUNTRY` | `Country` |
| `SUBJECT` | `Subject` |
| `CURRENCY` | `Currency` |
| `DENOMINATION` | `Denomination` |
| `RULING_AUTHORITY` | `RulingAuthority` |
| `RULING_AUTHORITY_GROUP` | `RulingAuthorityGroup` |
| `COMMEMORATED_EVENT` | `CommemoratedEvent` |
| `SERIES` | `Series` |
| `CATALOGUE` | `Catalogue` |
| `CATALOGUE_REFERENCE` | `CatalogueReference` |
| `AUTHOR` | `Author` |
| `ARTIST` | `Artist` |
| `SIGNATURE` | `Signature` |
| `PRINTER` | `Printer` |
| `MINT` | `Mint` |
| `MINTMARK` | `Mintmark` |
| `SPECIFIED_MINT` | `SpecifiedMint` |
| `MARK` | `Mark` |
| `COMPOSITION` | `Composition` |
| `COMPOSITION_TYPE` | `CompositionType` |
| `METAL` | `Metal` |
| `SHAPE` | `Shape` |
| `TECHNIQUE` | `Technique` |
| `LETTERING_SCRIPT` | `LetteringScript` |
| `YEAR` | `Year` |
| `CALENDAR` | `Calendar` |
| `SECTION` | `Section` |
| `COLLIGENDIS_USER` | `ColligendisUser` |
| `ACQUISITION_PLACE` | `AcquisitionPlace` |
| `STORAGE_LOCATION` | `StorageLocation` |
| `NUMISTA_COLLECTION_ITEM` | `NumistaCollectionItem` |

Versioned / deleted nodes reuse the same graph id with **additional** labels `*_VERSIONED` / `*_DELETED` as described above.

---

## Relationship types (by domain area)

### `NTYPE` (central type)

| Pattern | Constant |
|---------|----------|
| `NTYPE` → `COLLECTIBLE_TYPE` | `HAS_COLLECTIBLE_TYPE` |
| `NTYPE` → `ISSUER` | `ISSUED_BY` |
| `NTYPE` → `RULING_AUTHORITY` | `DURING_OF_RULER` |
| `NTYPE` → `ISSUING_ENTITY` | `ISSUED_BY_ISSUING_ENTITY` |
| `NTYPE` → `CURRENCY` | `HAS_CURRENCY` |
| `NTYPE` → `DENOMINATION` | `DENOMINATED_IN` |
| `NTYPE` → `COMMEMORATED_EVENT` | `COMMEMORATE_FOR` |
| `NTYPE` → `SERIES` | `WITH_SERIES` |
| `NTYPE` → `CATALOGUE_REFERENCE` | `HAS_CATALOGUE_REFERENCES` |
| `NTYPE` → `COMPOSITION` | `HAS_COMPOSITION` |
| `NTYPE` → `SHAPE` | `HAS_SHAPE` |
| `NTYPE` → `TECHNIQUE` | `HAS_TECHNIQUES` |
| `NTYPE` → `NTYPE_PART` | `HAS_OBVERSE`, `HAS_REVERSE`, `HAS_EDGE`, `HAS_WATERMARK` |
| `NTYPE` → `SPECIFIED_MINT` | `HAS_SPECIFIED_MINT` |
| `NTYPE` → `PRINTER` | `PRINTED_BY` |
| `NTYPE` → `VARIANT` | `HAS_VARIANT` |

`NType` also keeps a `currencyUuid` string field for lookups; the canonical graph link for currency is the `HAS_CURRENCY` relationship when created by services.

### `VARIANT`

| Pattern | Constant |
|---------|----------|
| `VARIANT` → `CALENDAR` | `WITH_CALENDAR` |
| `VARIANT` → `YEAR` | `DATED_AT` (mintage year in selected calendar) |
| `VARIANT` → `YEAR` | `DATED_FROM`, `DATED_TILL` (Gregorian year range only) |
| `VARIANT` → `SIGNATURE` | `WITH_SIGNATURE` |
| `VARIANT` → `MARK` | `WITH_MARK` |
| `VARIANT` → `CATALOGUE_REFERENCE` | `HAS_CATALOGUE_REFERENCES` |

Properties: `mintLetter` (from contribution `atelier` input; mint letter / workshop identifier for the variant row), `dated`, `dateMonth`, `dateDay`, `mintage`, `comment`.

Variant year values are stored on linked `YEAR` nodes, not as integer properties on `VARIANT`. `DATED_AT` points to a year in the calendar selected by `select#calendrier` (`WITH_CALENDAR`). When a non-Gregorian `YEAR` is created for `DATED_AT`, link it to the equivalent Gregorian year via `MATCH_UP_TO_GREGORIAN`. `DATED_FROM` / `DATED_TILL` always reference Gregorian calendar years (from `input[name^=dated]` / `input[name^=datef]`).

### `NTYPE_PART`

| Pattern | Constant |
|---------|----------|
| `NTYPE_PART` → `ARTIST` | `ENGRAVING_WAS_DONE_BY`, `DESIGN_WAS_DONE_BY` |
| `NTYPE_PART` → `LETTERING_SCRIPT` | `WRITE_ON_SCRIPT` |

### Issuer, country, subject hierarchy

| Pattern | Constant |
|---------|----------|
| `ISSUER` → `ISSUING_ENTITY` | `CONTAINS_ISSUING_ENTITY` |
| `ISSUER` → `CURRENCY` | `CONTAINS_CURRENCY` |
| `ISSUER` → `SUBJECT` | `RELATE_TO_SUBJECT` |
| `ISSUER` → `COUNTRY` | `RELATE_TO_COUNTRY` |
| `SUBJECT` → `COUNTRY` | `RELATE_TO_COUNTRY` |
| `SUBJECT` → `SUBJECT` | `PARENT_SUBJECT` |
| `COUNTRY` → `SUBJECT` | `PARENT_SUBJECT` |

### Currency, denomination, circulation years

| Pattern | Constant |
|---------|----------|
| `DENOMINATION` → `CURRENCY` | `UNDER_CURRENCY` |
| `CURRENCY` → `ISSUER` | `CIRCULATE_WHEN_BEEN` |
| `CURRENCY` → `YEAR` | `CIRCULATED_FROM`, `CIRCULATED_TILL` |

### Ruling authority and issuing entity

| Pattern | Constant |
|---------|----------|
| `RULING_AUTHORITY` → `ISSUER` | `RULES_WHEN_BEEN` |
| `RULING_AUTHORITY` → `RULING_AUTHORITY_GROUP` | `GROUP_BY` |
| `RULING_AUTHORITY` → `YEAR` | `RULES_FROM`, `RULES_TILL` |
| `ISSUING_ENTITY` → `ISSUER` | `ISSUES_WHEN_BEEN` |

### Catalogues and references

| Pattern | Constant |
|---------|----------|
| `CATALOGUE` → `AUTHOR` | `WRITTEN_BY` (see `CatalogueService`) |
| `CATALOGUE_REFERENCE` → `CATALOGUE` | `REFERENCE_FROM` |

`Author` also declares `AUTHORED` and `EDITED` toward `Catalogue`; there are no service references in the codebase yet, so treat those as **reserved** unless you add matching write paths.

### Minting

| Pattern | Constant |
|---------|----------|
| `MINT` → `MINTMARK` | `HAS_MINTMARK` |
| `SPECIFIED_MINT` → `MINT` | `WITH_MINT` |
| `SPECIFIED_MINT` → `MINTMARK` | `WITH_MINTMARK` |

### Composition (materials)

| Pattern | Constant |
|---------|----------|
| `COMPOSITION` → `COMPOSITION_TYPE` | `HAS_COMPOSITION_TYPE` |
| `COMPOSITION` → `METAL` | `PART1_IS_MADE_OF`, `PART2_IS_MADE_OF`, `PART3_IS_MADE_OF`, `PART4_IS_MADE_OF` |

### Collectible type tree

| Pattern | Constant |
|---------|----------|
| `COLLECTIBLE_TYPE` → `COLLECTIBLE_TYPE` | `HAS_COLLECTIBLE_TYPE_CHILD` |

### Calendar model

| Pattern | Constant |
|---------|----------|
| `YEAR` → `CALENDAR` | `TO_NUMBER_IN` |
| `YEAR` → `YEAR` | `MATCH_UP_TO_GREGORIAN` |

`YEAR` nodes store the calendar-specific year in property `dateYear` (not `value`). Non-Gregorian years link to their Gregorian equivalent via `MATCH_UP_TO_GREGORIAN` (Java field `sameGregorian`). Gregorian conversion uses `Calendar.toGregorianShift`: `gregorianDateYear ≈ dateYear + toGregorianShift`.

### User collection

| Pattern | Constant |
|---------|----------|
| `COLLIGENDIS_USER` → `ACQUISITION_PLACE` | `HAS_ACQUISITION_PLACE` |
| `COLLIGENDIS_USER` → `STORAGE_LOCATION` | `HAS_STORAGE_LOCATION` |
| `NUMISTA_COLLECTION_ITEM` → `NTYPE` | `FOR_NTYPE` |
| `NUMISTA_COLLECTION_ITEM` → `VARIANT` | `FOR_VARIANT` |
| `NUMISTA_COLLECTION_ITEM` → `ACQUISITION_PLACE` | `ACQUISITION_IN` |
| `NUMISTA_COLLECTION_ITEM` → `STORAGE_LOCATION` | `STORAGE_IN` |


### Version chain

| Pattern | Meaning |
|---------|---------|
| `*` → `*` | `PREVIOUS_VERSION` — links a new node revision to the prior node (see `AbstractService#updateNodeProperties`) |

---

## Keeping this document accurate

1. **Labels and relationship names** are defined as `public static final String` constants on the Java model classes; new types should add a `LABEL` and relationship constants there first.
2. **Write behavior** (properties on relationships, soft-delete suffixes, versioning) is centralized in `AbstractService`; behavior changes there should be reflected here.

## Related files

- `com.colligendis.server.database.AbstractNode` — property serialization rules (`uuid`, timestamps, enums as strings).
- `com.colligendis.server.database.AbstractService` — create/update/delete/relationship Cypher.
- `com.colligendis.server.config.Neo4jConfig` — driver bean.
- `com.colligendis.server.database.numista.model.*` — catalogue domain labels and relationship type constants.
