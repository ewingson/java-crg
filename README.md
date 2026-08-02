# java-crg
cross reference generator java july 26
- - - - 
pseudocode
- - - -
## Goal

Scan a given source-code text file, find every variable that is declared with
a fixed keyword (example: `var`), and produce a cross-reference matrix that
lists, for every variable, all line numbers on which it occurs.

**Precondition:** the analyzed source file must declare its variables using
the chosen keyword (e.g. `var name`), so that declarations can be detected
and indexed reliably.

The algorithm runs in two passes over the file:

1. **Declaration pass** — find the keyword, extract each variable name, and
   record its declaration line.
2. **Reference pass** — for every extracted variable name, scan the whole
   file again and record every line on which that exact identifier appears.

---

## Definitions

- `keyword` — the marker used for variable declarations. Example used
  throughout: `"var"`.
- `zeile[index]` — array of source lines, `index` from `0` to
  `lineCount - 1` (EOF-terminated).
- `variablenname[i]` — array of distinct variable names found,
  `i` from `0` to `numberOfVariables - 1`.
- `vorkommen[i][j]` — cross-reference matrix: for variable `i`, the list of
  line numbers `j` on which it occurs.
- ASCII reference points used for character checks: `' '` = 32, `'A'` = 65
  (i.e. plain ASCII comparisons, no locale-specific handling).

---

## Phase 1 — Read the file

```
1.  open source file
2.  index := 0
3.  while not EOF:
4.      zeile[index] := read next line
5.      index := index + 1
6.  lineCount := index
    // we now have zeile[0..lineCount-1]
```

---

## Phase 2 — Find declarations and extract variable names

```
7.  varCount := 0
8.  for i := 0 to lineCount - 1:
9.      line := zeile[i]
10.     for pos := 0 to length(line) - 4:
11.         // core check, char by char (ASCII):
12.         if line[pos]   == 'v'
13.         and line[pos+1] == 'a'
14.         and line[pos+2] == 'r'
15.         and line[pos+3] == ' ':            // space, ascii 32
16.             nameStart := pos + 4
17.             nameEnd   := nameStart
18.             while nameEnd < length(line)
19.                   and line[nameEnd] is a valid identifier char:
20.                 nameEnd := nameEnd + 1
21.             name := substring(line, nameStart, nameEnd)
22.             if name is not empty
23.             and name not already in variablenname[]:
24.                 variablenname[varCount] := name
25.                 declarationLine[varCount] := i
26.                 varCount := varCount + 1
    // we now have variablenname[0..varCount-1] with their declaration lines
```

*(Illustrates the substring convention used above:
`substring("keyword", 2, 4) = "ywor"` — start index 2, length 4.)*

---

## Phase 3 — Build the cross-reference matrix

```
27. for i := 0 to varCount - 1:
28.     j := 0
29.     for lineIdx := 0 to lineCount - 1:
30.         line := zeile[lineIdx]
31.         searchTerm := variablenname[i]     // replaces keyword 'var'
32.                                             // as the new search string
33.         pos := 0
34.         while pos <= length(line) - length(searchTerm):
35.             if line[pos .. pos+length(searchTerm)-1] == searchTerm
36.             and (pos == 0 or line[pos-1] is not an identifier char)
37.             and (char after match is not an identifier char):
38.                 vorkommen[i][j] := lineIdx
39.                 j := j + 1
40.             pos := pos + 1
41.     occurrenceCount[i] := j
    // vorkommen[i][0..occurrenceCount[i]-1] now holds every line
    // on which variablenname[i] occurs, including its declaration
```

---

## Phase 4 — Output

```
42. print "Cross-Reference Matrix"
43. for i := 0 to varCount - 1:
44.     print variablenname[i], "declared at line", declarationLine[i]
45.     print "  occurs on lines:", vorkommen[i][0 .. occurrenceCount[i]-1]
46. if output file requested:
47.     write the same matrix to file
```

---

## Notes / Optional extensions

- A search string could alternatively be supplied as a URL parameter
  (`?q=searchstring`) if the tool is exposed as a small web service instead
  of a CLI tool — not part of the core algorithm above.
- The keyword (`"var"`) is a configurable constant, not hard-coded logic —
  swapping it lets the same algorithm index declarations using any other
  keyword (`let`, `int`, `dim`, …).
- Identifier-boundary checks (steps 36–37) prevent `count` from matching
  inside `recount` — this is what makes phase 3 a true whole-word
  cross-reference rather than a naive substring search.
