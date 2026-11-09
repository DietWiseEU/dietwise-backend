# DietWise: Publish the Working Copy

`dietwise-publish` is a standalone command-line tool that **publishes** the backoffice **Working Copy** to the live
master data. It does not write to the database itself. It reads the database and prints to standard output a single
Liquibase changeset that:

1. checks that the Working Copy has not changed since the changeset was generated,
2. applies every Staged Change to the master tables (inserts, updates, deletes),
3. clears the Working Copy tables,
4. carries an explicit rollback that restores both the master data and the Working Copy.

The changeset is then committed alongside the rest of the Liquibase changelog and applied like any other migration.
That way every publish is reviewed, versioned in git, applied the same way in every environment and can be rolled back.

See `CONTEXT.md` at the repository root for the terms *Working Copy*, *Staged Change* and *Publish*.


## Usage

### Prerequisites

- Java 25 and Maven, as for the rest of the project.
- Network access to the database to publish from, with a user that can `SELECT` from the master and Working Copy
  (`*_WC`) tables. The tool only reads.

### Generating the changeset

The tool reads its connection from the same environment variables as the application:

| Variable                      | Example                                |
|-------------------------------|----------------------------------------|
| `QUARKUS_DATASOURCE_JDBC_URL` | `jdbc:postgresql://localhost/dietwise` |
| `QUARKUS_DATASOURCE_USERNAME` | `dietwise`                             |
| `QUARKUS_DATASOURCE_PASSWORD` | `dietwise`                             |

From the repository root:

```bash
QUARKUS_DATASOURCE_JDBC_URL=... QUARKUS_DATASOURCE_USERNAME=... QUARKUS_DATASOURCE_PASSWORD=... \
mvn -q -pl dietwise-tools/dietwise-publish compile exec:java \
	> dietwise-container/dietwise-dao-hibernate-reactive/src/main/resources/changelogs/YYYYMMDD_publish.xml
```

Keep `-q`: Maven writes its own log to standard output, and without `-q` it ends up in the generated file. The tool
itself writes only the changelog XML to standard output and all diagnostics to standard error.

Two optional positional arguments set the changeset id and author:

```bash
mvn -q -pl dietwise-tools/dietwise-publish compile exec:java -Dexec.args="<changeset-id> <author>"
```

| Argument       | Default                                                              |
|----------------|----------------------------------------------------------------------|
| `changeset-id` | `publish_working_copy_<yyyyMMddHHmmss>` (local time of generation)   |
| `author`       | `publish-tool`                                                       |

### Exit status

| Status | Meaning                                                                                                     |
|--------|-------------------------------------------------------------------------------------------------------------|
| `0`    | The changeset was written to standard output; **or** the Working Copy is empty, in which case nothing is written to standard output and `The Working Copy is empty; nothing to publish.` goes to standard error. |
| `1`    | A database error; the message goes to standard error.                                                       |
| `2`    | One of the three environment variables is missing.                                                         |

Because an empty Working Copy also exits with `0`, check that the output file is not empty before committing it.

### Applying the changeset

1. Include the generated file at the end of the master changelog,
   `dietwise-container/dietwise-dao-hibernate-reactive/src/main/resources/changelog.xml`:

   ```xml
   <include relativeToChangelogFile="true" file="changelogs/YYYYMMDD_publish.xml"/>
   ```

2. Apply it, either by starting the application (runtime Liquibase migrations are enabled) or with the `dbupdate`
   profile, as described in the root `README.md`:

   ```bash
   mvn process-resources -Pdbupdate,dw-local-postgres
   ```

3. **Restart the application.** It reads the recommendations once and keeps them for its lifetime, so until it
   restarts it goes on assessing recipes against the previous ones.

Generate and apply against the **same** database, and do not let editors stage further changes in between. If the
Working Copy changes after generation, the changeset's precondition fails and Liquibase halts with
*"The Working Copy changed after this changeset was generated; regenerate and re-apply."* In that case delete the
generated file, generate it again and re-apply.

### Rolling back

The changeset carries its own `<rollback>`, so a publish is undone with a regular Liquibase rollback of one changeset
(see *Rolling back changes* in the root `README.md`, with `-Dliquibase.rollbackCount=1`). The rollback deletes the
rows the publish inserted, restores the previous values of the rows it updated, re-inserts the rows it deleted and
re-populates the Working Copy as it was before the publish.


## How it works

The tool runs in three stages, each in its own class:

| Stage   | Class               | Does                                                                                         |
|---------|---------------------|----------------------------------------------------------------------------------------------|
| Read    | `DatabaseReader`    | Reads every master and Working Copy table listed in the registry, all values as text.        |
| Plan    | `PublishPlanner`    | Diffs each Working Copy table against its master table into a `PublishPlan`. Pure, no DB.    |
| Render  | `ChangelogRenderer` | Renders the plan as one Liquibase XML changeset with precondition and rollback.              |

`PublishWorkingCopy` is the entry point that wires them together.

### The table registry

The tool does **not** discover the schema. `eu.dietwise.tools.publish.schema.Tables` is a hand-maintained registry of
every master table that has a Working Copy mirror. For each pair (`MirrorSpec`) it records:

- the columns of both tables, with their types, and whether each master column is `NOT NULL`;
- the primary key of both tables;
- the publish kind (see below);
- the foreign-key rank (`order`): mirrors of a lower order are inserted before mirrors of a higher order and deleted
  after them, so a parent always exists before its children;
- the foreign keys whose parent must exist (`parentRefs`).

### Publish kinds

| Kind               | Working Copy row means                                       | Publishes as                                                                                     |
|--------------------|--------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `SNAPSHOT`         | The full proposed state of the entity.                       | Insert if the key is absent from master, otherwise update the columns that differ. Never deletes. |
| `LINK_DELTA`       | A link to add (`present = true`) or remove (`present = false`). | Insert the link if missing, delete it if present; otherwise nothing.                          |
| `NULLABLE_PAYLOAD` | Like `SNAPSHOT`, but an all-null payload means "remove".     | All-null payload deletes the master row; otherwise as `SNAPSHOT`.                                |

The *payload* columns of a mirror are its Working Copy columns other than the primary key and `version`. Only payload
columns are compared and written; master columns that have no Working Copy counterpart are left alone on update.

### Data cleanup

Before diffing, every text value in both master and Working Copy is cleaned: leading and trailing whitespace is
trimmed and every run of whitespace (including tabs and newlines) becomes a single space (`StringCleanup`). A change
that is only whitespace therefore does not count as a change, and the published values are always clean.

### Rows that are dropped

The planner skips a Working Copy row, so the generated changeset never violates a database constraint, when:

- one of its `parentRefs` points at a row that will not exist in master after the publish (neither already in master
  nor inserted by the same publish); or
- publishing it would write `NULL` into a master column marked `notNull()`.

A dropped row is **not** published, but it is still cleared from the Working Copy, and it is **not** restored on
rollback. The tool does not report dropped rows; compare the Working Copy with the generated changeset if in doubt.

### The Working Copy fingerprint

The precondition compares a fingerprint of the Working Copy at apply time with its value at generation time. The
fingerprint is the sum, over all Working Copy tables, of the row count plus, for tables with a `version` column, the
sum of the `version` values. It detects added or removed rows and any edit that increments `version`. It does not
detect a change that leaves both unchanged, e.g. flipping `present` on a link row of the `LINK_DELTA` table (which has
no `version`).


## Maintaining the tool

Whenever a migration adds, removes or changes a Working Copy table or its master counterpart, update `Tables`:

- add or change the columns of both sides, marking non-nullable master columns with `.notNull()`;
- for a new mirror, choose its publish kind and set its `order` so it sorts after every mirrored table it references;
- add a `ParentRef` for each foreign key whose parent existence must be enforced (the parent must have a single-column
  primary key).

The tests catch drift between the registry and the real schema:

| Test                                 | Checks                                                                                     |
|--------------------------------------|--------------------------------------------------------------------------------------------|
| `PublishPlannerTest`                 | The planning rules, on in-memory rows.                                                     |
| `ChangelogRendererTest`              | The rendered XML.                                                                          |
| `StringCleanupTest`                  | Text cleanup.                                                                              |
| `RegistryNullabilityTest`            | Every master column's nullability in the registry against a real database.                 |
| `PublishWorkingCopyRoundTripTest`    | End to end on a real database: generate, apply and roll back with Liquibase.               |

The last two run the application's Liquibase changelog on PostgreSQL through Testcontainers, so they need Docker:

```bash
mvn -pl dietwise-tools/dietwise-publish -am verify
```
