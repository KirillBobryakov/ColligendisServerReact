#!/usr/bin/env python3
"""Generate NumistaGradingMark.java from Numista collec_form_grading_mark HTML options."""

import re
import sys
from pathlib import Path

OPTION_RE = re.compile(
    r'<option\s+value="(\d+)"(?:\s+data-service-id="(\d+)")?[^>]*>\s*([^<]+?)\s*</option>',
    re.IGNORECASE,
)


def java_escape(s: str) -> str:
    return s.replace("\\", "\\\\").replace("\"", "\\\"")


def to_enum_name(value: str, service: str, label: str, used: set[str]) -> str:
    base = re.sub(r"[^A-Za-z0-9]+", "_", label.upper()).strip("_")
    if not base or base[0].isdigit():
        base = "MARK_" + base
    if service:
        name = f"{base}_S{service}"
    else:
        name = base
    if not name:
        name = f"MARK_{value}"
    if name in used:
        name = f"{name}_V{value}"
    used.add(name)
    return name


def main() -> None:
    html_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent / "numista-grading-marks.html"
    html = html_path.read_text(encoding="utf-8")

    seen: set[tuple[str, str, str]] = set()
    entries: list[tuple[str, str, str, str]] = []
    used_names: set[str] = set()

    for value, service, label in OPTION_RE.findall(html):
        label = " ".join(label.split())
        service = service or ""
        key = (value, service, label)
        if key in seen:
            continue
        seen.add(key)
        if value == "0" and not label:
            enum_name = "NONE"
        else:
            enum_name = to_enum_name(value, service, label, used_names)
        entries.append((enum_name, value, service, label))

    entries.sort(key=lambda e: (int(e[1]), e[2], e[3]))

    out = Path(__file__).parent.parent / "src/main/java/com/colligendis/server/parser/numista/collection/NumistaGradingMark.java"
    lines = [
        "package com.colligendis.server.parser.numista.collection;",
        "",
        "import java.util.Arrays;",
        "import java.util.List;",
        "import java.util.Optional;",
        "",
        "import org.springframework.util.StringUtils;",
        "",
        "/**",
        " * Values for Numista {@code collec_form_grading_mark} / {@code gradingMark}.",
        " * Each mark is tied to a grading service via {@link #serviceId} ({@link NumistaGradingService#getNumistaValue()}).",
        " */",
        "public enum NumistaGradingMark {",
    ]

    for i, (enum_name, value, service, label) in enumerate(entries):
        comma = "," if i < len(entries) - 1 else ";"
        service_arg = f'"{service}"' if service else '""'
        lines.append(f'\t{enum_name}("{value}", {service_arg}, "{java_escape(label)}"){comma}')

    lines.extend(
        [
            "",
            "\tprivate final String numistaValue;",
            "\tprivate final String serviceId;",
            "\tprivate final String label;",
            "",
            "\tNumistaGradingMark(String numistaValue, String serviceId, String label) {",
            "\t\tthis.numistaValue = numistaValue;",
            "\t\tthis.serviceId = serviceId;",
            "\t\tthis.label = label;",
            "\t}",
            "",
            "\tpublic String getNumistaValue() {",
            "\t\treturn numistaValue;",
            "\t}",
            "",
            "\t/** {@link NumistaGradingService} numista value ({@code data-service-id}). */",
            "\tpublic String getServiceId() {",
            "\t\treturn serviceId;",
            "\t}",
            "",
            "\tpublic String getLabel() {",
            "\t\treturn label;",
            "\t}",
            "",
            "\tpublic Optional<NumistaGradingService> getGradingService() {",
            "\t\treturn NumistaGradingService.fromNumistaValue(serviceId);",
            "\t}",
            "",
            "\tpublic static Optional<NumistaGradingMark> fromNumistaValue(String value) {",
            "\t\tif (!StringUtils.hasText(value) || \"0\".equals(value.trim())) {",
            "\t\t\treturn Optional.empty();",
            "\t\t}",
            "\t\tString trimmed = value.trim();",
            "\t\treturn Arrays.stream(values())",
            "\t\t\t\t.filter(m -> m.numistaValue.equals(trimmed))",
            "\t\t\t\t.findFirst();",
            "\t}",
            "",
            "\tpublic static Optional<NumistaGradingMark> fromNumistaValueAndService(",
            "\t\t\tString value, NumistaGradingService gradingService) {",
            "\t\tif (!StringUtils.hasText(value) || gradingService == null || \"0\".equals(value.trim())) {",
            "\t\t\treturn Optional.empty();",
            "\t\t}",
            "\t\tString trimmed = value.trim();",
            "\t\tString serviceValue = gradingService.getNumistaValue();",
            "\t\treturn Arrays.stream(values())",
            "\t\t\t\t.filter(m -> m.numistaValue.equals(trimmed) && m.serviceId.equals(serviceValue))",
            "\t\t\t\t.findFirst();",
            "\t}",
            "",
            "\tpublic static List<NumistaGradingMark> forService(NumistaGradingService gradingService) {",
            "\t\tif (gradingService == null) {",
            "\t\t\treturn List.of();",
            "\t\t}",
            "\t\tString serviceValue = gradingService.getNumistaValue();",
            "\t\treturn Arrays.stream(values())",
            "\t\t\t\t.filter(m -> m.serviceId.equals(serviceValue))",
            "\t\t\t\t.toList();",
            "\t}",
            "",
            "\tpublic static Optional<NumistaGradingMark> fromLabelAndService(String label, NumistaGradingService gradingService) {",
            "\t\tif (!StringUtils.hasText(label) || gradingService == null) {",
            "\t\t\treturn Optional.empty();",
            "\t\t}",
            "\t\tString trimmed = label.trim();",
            "\t\tString serviceValue = gradingService.getNumistaValue();",
            "\t\treturn Arrays.stream(values())",
            "\t\t\t\t.filter(m -> m.serviceId.equals(serviceValue) && m.label.equalsIgnoreCase(trimmed))",
            "\t\t\t\t.findFirst();",
            "\t}",
            "}",
            "",
        ]
    )

    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {len(entries)} marks to {out}")


if __name__ == "__main__":
    main()
