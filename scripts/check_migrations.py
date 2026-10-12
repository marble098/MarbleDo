#!/usr/bin/env python3
"""Offline consistency check between Room entities and the handwritten migrations.

Room's post-migration schema validation crashes the app on start-up when the SQL in a
migration disagrees with the entity annotation metadata (column default, nullability,
type affinity, or indices). Gradle unit tests never exercise migrations because they
cannot open an Android database, so this script statically cross-checks the two
sources of truth that genuinely exist in this repository:

  - core/data ... *Entity.kt  -> what Room expects (defaults, nullability, affinity, indices)
  - MarbleDatabase.kt         -> what the migrations actually execute

A failure here means a released migration would crash with
"Migration didn't properly handle" on user devices.
"""
from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DB_SOURCE_DIR = ROOT / "core/data/src/main/kotlin/com/marbledo/core/data/db"
DATABASE_SOURCE = DB_SOURCE_DIR / "MarbleDatabase.kt"

KOTLIN_TO_AFFINITY = {
    "String": "TEXT",
    "Int": "INTEGER",
    "Long": "INTEGER",
    "Boolean": "INTEGER",
    "Float": "REAL",
    "Double": "REAL",
    "ByteArray": "BLOB",
}

ENTITY_BLOCK = re.compile(r"@Entity\((?P<args>.*?)\)\s*data\s+class\s+(?P<cls>\w+)", re.DOTALL)
TABLE_NAME = re.compile(r"tableName\s*=\s*\"(?P<table>\w+)\"")
ENTITY_INDEX = re.compile(
    r"Index\(\s*value\s*=\s*\[(?P<cols>[^\]]+)\]\s*(?:,\s*unique\s*=\s*(?P<unique>true|false))?\s*\)"
)
COLUMN_INFO_PROP = re.compile(
    r"(?:@ColumnInfo\((?P<ci>[^)]*)\)\s*)?val\s+(?P<prop>\w+)\s*:\s*"
    r"(?P<type>String|Int|Long|Boolean|Float|Double|ByteArray)(?P<nullable>\?)?\s*[,)=]"
)
CI_NAME = re.compile(r"name\s*=\s*\"(?P<name>\w+)\"")
CI_DEFAULT = re.compile(r"defaultValue\s*=\s*\"(?P<default>(?:[^\"\\]|\\.)*)\"")

EXEC_SQL = re.compile(r"\bexecSQL\s*\(")
CREATE_TABLE = re.compile(r"^\s*CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(?P<table>\w+)`?", re.IGNORECASE)
ALTER_ADD_COLUMN = re.compile(
    r"^\s*ALTER\s+TABLE\s+`?(?P<table>\w+)`?\s+ADD\s+COLUMN\s+(?P<defn>.*)$",
    re.IGNORECASE | re.DOTALL,
)
CREATE_INDEX = re.compile(
    r"^\s*CREATE\s+(?P<unique>UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(?P<name>\w+)`?"
    r"\s+ON\s+`?(?P<table>\w+)`?\s*\((?P<cols>[^)]*)\)",
    re.IGNORECASE,
)
COLUMN_DEF = re.compile(
    r"^\s*`?(?P<name>\w+)`?\s+(?P<affinity>INTEGER|TEXT|REAL|BLOB)\b(?P<rest>.*)$",
    re.IGNORECASE | re.DOTALL,
)
TABLE_CONSTRAINT = re.compile(r"^\s*(PRIMARY\s+KEY|FOREIGN\s+KEY|UNIQUE\b|CHECK\b|CONSTRAINT\b)", re.IGNORECASE)
DEFAULT_CLAUSE = re.compile(
    r"\bDEFAULT\s+(?P<literal>'(?:[^'\\]|\\.)*'|\([^)]*\)|[^\s,)]+)", re.IGNORECASE
)
NOT_NULL_CLAUSE = re.compile(r"\bNOT\s+NULL\b", re.IGNORECASE)


@dataclass
class ExpectedColumn:
    file: str
    table: str
    name: str
    affinity: str
    nullable: bool
    default: str | None


@dataclass
class ExpectedIndex:
    file: str
    table: str
    columns: list[str]
    unique: bool

    @property
    def name(self) -> str:
        # Room derives index names this way when no explicit name is given.
        return "index_" + self.table + "_" + "_".join(self.columns)


@dataclass
class Expectations:
    columns: list[ExpectedColumn] = field(default_factory=list)
    indices: list[ExpectedIndex] = field(default_factory=list)


@dataclass
class FoundColumn:
    affinity: str
    rest: str
    statement: str


@dataclass
class FoundIndex:
    unique: bool
    columns: list[str]


@dataclass
class FoundSchema:
    tables: dict[str, dict[str, FoundColumn]] = field(default_factory=dict)
    indices: dict[str, FoundIndex] = field(default_factory=dict)


def _unquote_csv(raw: str) -> list[str]:
    return re.findall(r"\"(\w+)\"", raw)


def expectations_from_entity(file_label: str, source: str) -> Expectations:
    result = Expectations()
    entity = ENTITY_BLOCK.search(source)
    if entity is None:
        return result
    args = entity.group("args")
    table_match = TABLE_NAME.search(args)
    if table_match is None:
        return result
    table = table_match.group("table")

    for index_match in ENTITY_INDEX.finditer(args):
        result.indices.append(
            ExpectedIndex(
                file=file_label,
                table=table,
                columns=_unquote_csv(index_match.group("cols")),
                unique=index_match.group("unique") == "true",
            )
        )

    for prop in COLUMN_INFO_PROP.finditer(source):
        info = prop.group("ci") or ""
        name_match = CI_NAME.search(info)
        default_match = CI_DEFAULT.search(info)
        result.columns.append(
            ExpectedColumn(
                file=file_label,
                table=table,
                name=name_match.group("name") if name_match else prop.group("prop"),
                affinity=KOTLIN_TO_AFFINITY[prop.group("type")],
                nullable=prop.group("nullable") == "?",
                default=default_match.group("default") if default_match else None,
            )
        )
    return result


def _strip_kotlin_comments(source: str) -> str:
    """Remove // and /* */ comments while keeping string literals intact."""
    out: list[str] = []
    i = 0
    in_string = False
    while i < len(source):
        char = source[i]
        if in_string:
            out.append(char)
            if char == "\\" and i + 1 < len(source):
                out.append(source[i + 1])
                i += 2
                continue
            if char == '"':
                in_string = False
            i += 1
            continue
        if char == '"':
            in_string = True
            out.append(char)
            i += 1
            continue
        if source.startswith("//", i):
            while i < len(source) and source[i] != "\n":
                i += 1
            continue
        if source.startswith("/*", i):
            end = source.find("*/", i + 2)
            i = len(source) if end == -1 else end + 2
            continue
        out.append(char)
        i += 1
    return "".join(out)


def iter_sql_statements(database_source: str) -> list[str]:
    """Reconstruct each execSQL(...) call by concatenating its string literals."""
    statements = []
    source = _strip_kotlin_comments(database_source)
    for call in EXEC_SQL.finditer(source):
        depth = 1
        i = call.end()
        parts: list[str] = []
        while i < len(source) and depth:
            char = source[i]
            if char == '"':
                j = i + 1
                buf: list[str] = []
                while j < len(source) and source[j] != '"':
                    if source[j] == "\\" and j + 1 < len(source):
                        j += 1
                    buf.append(source[j])
                    j += 1
                parts.append("".join(buf))
                i = j + 1
                continue
            if char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
            i += 1
        statements.append("".join(parts))
    return statements


def _split_top_level(body: str) -> list[str]:
    parts, buf, depth = [], [], 0
    for char in body:
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        if char == "," and depth == 0:
            parts.append("".join(buf))
            buf = []
        else:
            buf.append(char)
    tail = "".join(buf).strip()
    if tail:
        parts.append(tail)
    return [part.strip() for part in parts if part.strip()]


def _balanced_parens_body(text: str, open_index: int) -> str:
    depth = 0
    for i in range(open_index, len(text)):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return text[open_index + 1 : i]
    return text[open_index + 1 :]


def parse_migration_schema(database_source: str) -> FoundSchema:
    schema = FoundSchema()
    for statement in iter_sql_statements(database_source):
        create = CREATE_TABLE.match(statement)
        if create is not None:
            table = create.group("table")
            body = _balanced_parens_body(statement, statement.index("(", create.end()))
            columns = schema.tables.setdefault(table, {})
            for segment in _split_top_level(body):
                if TABLE_CONSTRAINT.match(segment):
                    continue
                column = COLUMN_DEF.match(segment)
                if column is not None:
                    columns[column.group("name")] = FoundColumn(
                        affinity=column.group("affinity").upper(),
                        rest=column.group("rest"),
                        statement=segment,
                    )
            continue

        alter = ALTER_ADD_COLUMN.match(statement)
        if alter is not None:
            column = COLUMN_DEF.match(alter.group("defn"))
            if column is not None:
                schema.tables.setdefault(alter.group("table"), {})[column.group("name")] = FoundColumn(
                    affinity=column.group("affinity").upper(),
                    rest=column.group("rest"),
                    statement=alter.group("defn").strip(),
                )
            continue

        index = CREATE_INDEX.match(statement)
        if index is not None:
            schema.indices[index.group("name")] = FoundIndex(
                unique=index.group("unique") is not None,
                columns=[re.sub(r"[`\s]", "", part) for part in index.group("cols").split(",")],
            )
    return schema


def check(entity_sources: dict[str, str], database_source: str) -> list[str]:
    """Return a list of migration/entity mismatches; empty means Room validation would pass."""
    problems: list[str] = []
    expected = Expectations()
    for label, source in sorted(entity_sources.items()):
        parsed = expectations_from_entity(label, source)
        expected.columns.extend(parsed.columns)
        expected.indices.extend(parsed.indices)

    found = parse_migration_schema(database_source)

    for column in expected.columns:
        # Columns absent from every migration predate the first tracked schema (v1);
        # Room generated that baseline itself, so only entities with explicit defaults
        # can still be violated there.
        table = found.tables.get(column.table, {})
        definition = table.get(column.name)
        if definition is None:
            if column.default is not None:
                problems.append(
                    f"{column.file}: column '{column.name}' declares "
                    f"@ColumnInfo(defaultValue = \"{column.default}\") but no migration "
                    f"defines {column.table}.{column.name}, so Room would compare against "
                    f"defaultValue 'undefined'"
                )
            continue
        if definition.affinity != column.affinity:
            problems.append(
                f"{column.file}: column '{column.name}' is {column.affinity} in the entity "
                f"but the migration defines it as {definition.affinity} ('{definition.statement.strip()}')"
            )
        not_null_sql = NOT_NULL_CLAUSE.search(definition.rest) is not None
        if not_null_sql == column.nullable:
            problems.append(
                f"{column.file}: column '{column.name}' nullability mismatch: entity "
                f"nullable={column.nullable} but the migration has '{definition.statement.strip()}'"
            )
        default_sql = DEFAULT_CLAUSE.search(definition.rest)
        sql_literal = default_sql.group("literal") if default_sql else None
        if column.default is None and sql_literal is not None:
            problems.append(
                f"{column.file}: column '{column.name}' has DEFAULT {sql_literal} in a "
                f"migration but no @ColumnInfo(defaultValue) on the entity"
            )
        elif column.default is not None:
            if sql_literal is None:
                problems.append(
                    f"{column.file}: column '{column.name}' expects DEFAULT {column.default} "
                    f"(@ColumnInfo) but the migration definition '{definition.statement.strip()}' "
                    f"has none — this crashes with \"Migration didn't properly handle\""
                )
            elif column.default != sql_literal:
                problems.append(
                    f"{column.file}: column '{column.name}' expects DEFAULT {column.default} "
                    f"but the migration declares DEFAULT {sql_literal}"
                )

    for index in expected.indices:
        found_index = found.indices.get(index.name)
        if found_index is None:
            problems.append(
                f"{index.file}: entity index '{index.name}' on {index.table}({', '.join(index.columns)}) "
                f"is never created by a migration"
            )
            continue
        if found_index.unique != index.unique:
            problems.append(
                f"{index.file}: index '{index.name}' unique={index.unique} in the entity "
                f"but unique={found_index.unique} in the migration"
            )
        if [c.lower() for c in found_index.columns] != [c.lower() for c in index.columns]:
            problems.append(
                f"{index.file}: index '{index.name}' columns {found_index.columns} in the migration "
                f"do not match the entity columns {index.columns}"
            )

    return problems


def load_entity_sources(db_dir: Path = DB_SOURCE_DIR) -> dict[str, str]:
    return {
        str(path.relative_to(ROOT)): path.read_text(encoding="utf-8")
        for path in sorted(db_dir.glob("*Entity.kt"))
    }


def main() -> int:
    entity_sources = load_entity_sources()
    database_source = DATABASE_SOURCE.read_text(encoding="utf-8")
    problems = check(entity_sources, database_source)
    columns_checked = sum(
        len(expectations_from_entity(label, src).columns) for label, src in entity_sources.items()
    )
    print(json.dumps({"entities_checked": len(entity_sources), "columns_checked": columns_checked, "problems": problems}, indent=2))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
