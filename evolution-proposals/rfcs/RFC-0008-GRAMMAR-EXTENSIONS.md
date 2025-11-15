# RFC-0008 Grammar Extensions

This document describes the required ANTLR grammar modifications to support Enhanced CQL Features.

## Overview

The implementation requires updates to two ANTLR3 grammar files:
- `src/antlr/Lexer.g` - Add new keywords
- `src/antlr/Parser.g` - Add new grammar rules

## 1. Lexer.g Extensions

Add the following keyword definitions to `src/antlr/Lexer.g`:

```antlr
// Window function keywords
K_OVER:         O V E R;
K_WINDOW:       W I N D O W;
K_ROWS:         R O W S;
K_RANGE:        R A N G E;
K_PRECEDING:    P R E C E D I N G;
K_FOLLOWING:    F O L L O W I N G;
K_UNBOUNDED:    U N B O U N D E D;
K_CURRENT:      C U R R E N T;
K_ROW:          R O W;

// Recursive CTE keywords
K_RECURSIVE:    R E C U R S I V E;
K_UNION:        U N I O N;
K_ALL:          A L L;

// Enhanced aggregate keywords
K_FILTER:       F I L T E R;
K_WITHIN:       W I T H I N;
K_GROUP:        G R O U P; // May already exist - check
K_STDDEV:       S T D D E V;
K_VARIANCE:     V A R I A N C E;
K_MEDIAN:       M E D I A N;
K_PERCENTILE:   P E R C E N T I L E;

// Window function names
K_ROW_NUMBER:   R O W '_' N U M B E R;
K_RANK:         R A N K;
K_DENSE_RANK:   D E N S E '_' R A N K;
K_PERCENT_RANK: P E R C E N T '_' R A N K;
K_NTILE:        N T I L E;
K_LAG:          L A G;
K_LEAD:         L E A D;
K_FIRST_VALUE:  F I R S T '_' V A L U E;
K_LAST_VALUE:   L A S T '_' V A L U E;
K_NTH_VALUE:    N T H '_' V A L U E;
K_CUME_DIST:    C U M E '_' D I S T;
```

**Note**: Keywords `K_WITH`, `K_BY`, and `K_ORDER` already exist in the lexer.

## 2. Parser.g Extensions

### 2.1 Common Table Expressions (WITH clause)

Modify the `selectStatement` rule to support optional WITH clause:

```antlr
selectStatement returns [SelectStatement.RawStatement expr]
    @init {
        List<CTEClause> ctes = new ArrayList<>();
        Term.Raw limit = null;
        Term.Raw perPartitionLimit = null;
        List<Ordering.Raw> orderings = new ArrayList<>();
        List<Selectable.Raw> groups = new ArrayList<>();
        boolean allowFiltering = false;
        boolean isJson = false;
        SelectOptions options = new SelectOptions();
        stmtBegins();
    }
    : (withClause[ctes])?
      K_SELECT
        // json is a valid column name. By consequence, we need to resolve the ambiguity for "json - json"
      ( (K_JSON selectClause)=> K_JSON { isJson = true; } )? sclause=selectClause
      K_FROM cf=columnFamilyName
      ( K_WHERE wclause=whereClause )?
      ( K_GROUP K_BY groupByClause[groups] ( ',' groupByClause[groups] )* )?
      ( K_ORDER K_BY orderByClause[orderings] ( ',' orderByClause[orderings] )* )?
      ( K_PER K_PARTITION K_LIMIT rows=intValue { perPartitionLimit = rows; } )?
      ( K_LIMIT rows=intValue { limit = rows; } )?
      ( K_ALLOW K_FILTERING  { allowFiltering = true; } )?
      ( K_WITH properties[options] )?
      {
          SelectStatement.Parameters params = new SelectStatement.Parameters(orderings,
                                                                             groups,
                                                                             $sclause.isDistinct,
                                                                             allowFiltering,
                                                                             isJson,
                                                                             null);
          WhereClause where = wclause == null ? WhereClause.empty() : wclause.build();

          if (ctes.isEmpty())
          {
              $expr = new SelectStatement.RawStatement(cf, params, $sclause.selectors, where, limit, perPartitionLimit, stmtSrc(), options);
          }
          else
          {
              SelectStatement.RawStatement mainQuery = new SelectStatement.RawStatement(cf, params, $sclause.selectors, where, limit, perPartitionLimit, stmtSrc(), options);
              $expr = new SelectStatementWithCTE.RawStatement(ctes, mainQuery);
          }
      }
    ;
```

Add new grammar rules for WITH clause:

```antlr
withClause[List<CTEClause> ctes]
    : K_WITH (K_RECURSIVE { isRecursive = true; })? cteDefinition[ctes] (',' cteDefinition[ctes])*
    ;

cteDefinition[List<CTEClause> ctes]
    @init {
        List<ColumnIdentifier> columns = null;
        boolean isRecursive = false;
    }
    : name=ident
      ('(' c1=ident { columns = new ArrayList<>(); columns.add(c1); }
           (',' cn=ident { columns.add(cn); })* ')')?
      K_AS '(' stmt=selectStatement ')'
      {
          ctes.add(new SelectStatementWithCTE.RawCTEClause($name.text, stmt, columns, isRecursive));
      }
    ;
```

### 2.2 Window Functions (OVER clause)

Modify the `unaliasedSelector` rule to support window functions:

```antlr
unaliasedSelector returns [Selectable.Raw s]
    : a=selectionAddition
      (K_OVER '(' windowSpec ')' { $s = new WindowFunctionSelector.Raw(a, $windowSpec.spec); })?
      { if ($s == null) $s = a; }
    ;
```

Add window specification rules:

```antlr
windowSpec returns [WindowFunctionSelector.RawWindowSpec spec]
    @init {
        List<Selectable.Raw> partitionBy = null;
        List<WindowFunctionSelector.OrderingRaw> orderBy = null;
        WindowFunctionSelector.RawWindowFrame frame = null;
    }
    : (K_PARTITION K_BY partitionByList[partitionBy])?
      (K_ORDER K_BY windowOrderByList[orderBy])?
      (windowFrame { frame = $windowFrame.frame; })?
      {
          $spec = new WindowFunctionSelector.RawWindowSpec(partitionBy, orderBy, frame);
      }
    ;

partitionByList[List<Selectable.Raw> list]
    : { list = new ArrayList<>(); }
      s1=unaliasedSelector { list.add(s1); }
      (',' sn=unaliasedSelector { list.add(sn); })*
    ;

windowOrderByList[List<WindowFunctionSelector.OrderingRaw> list]
    : { list = new ArrayList<>(); }
      o1=windowOrdering { list.add(o1); }
      (',' on=windowOrdering { list.add(on); })*
    ;

windowOrdering returns [WindowFunctionSelector.OrderingRaw ordering]
    @init { boolean ascending = true; }
    : s=unaliasedSelector (K_ASC { ascending = true; } | K_DESC { ascending = false; })?
      {
          $ordering = new WindowFunctionSelector.OrderingRaw(s, ascending);
      }
    ;

windowFrame returns [WindowFunctionSelector.RawWindowFrame frame]
    @init {
        String type = "RANGE";
        String startType = "UNBOUNDED PRECEDING";
        Integer startOffset = null;
        String endType = "CURRENT ROW";
        Integer endOffset = null;
    }
    : (K_ROWS { type = "ROWS"; } | K_RANGE { type = "RANGE"; })?
      K_BETWEEN
      start=frameBound { startType = $start.type; startOffset = $start.offset; }
      K_AND
      end=frameBound { endType = $end.type; endOffset = $end.offset; }
      {
          $frame = new WindowFunctionSelector.RawWindowFrame(type, startType, startOffset, endType, endOffset);
      }
    ;

frameBound returns [String type, Integer offset]
    @init { $offset = null; }
    : K_UNBOUNDED K_PRECEDING { $type = "UNBOUNDED PRECEDING"; }
    | K_UNBOUNDED K_FOLLOWING { $type = "UNBOUNDED FOLLOWING"; }
    | K_CURRENT K_ROW { $type = "CURRENT ROW"; }
    | n=intValue K_PRECEDING { $type = "PRECEDING"; $offset = Integer.parseInt(n.getText()); }
    | n=intValue K_FOLLOWING { $type = "FOLLOWING"; $offset = Integer.parseInt(n.getText()); }
    ;
```

### 2.3 FILTER Clause for Aggregates

Modify aggregate function parsing to support FILTER clause:

```antlr
functionArgs returns [List<Selectable.Raw> args, boolean hasFilter, Selectable.Raw filterCondition]
    @init {
        $args = new ArrayList<>();
        $hasFilter = false;
        $filterCondition = null;
    }
    : ( arg1=unaliasedSelector { $args.add(arg1); }
        (',' argn=unaliasedSelector { $args.add(argn); })* )?
      (K_FILTER '(' K_WHERE fc=whereClauseSimple ')' { $hasFilter = true; $filterCondition = fc; })?
    ;
```

### 2.4 Window Function Names

Add specific window function parsing:

```antlr
windowFunctionName returns [String name]
    : K_ROW_NUMBER { $name = "ROW_NUMBER"; }
    | K_RANK { $name = "RANK"; }
    | K_DENSE_RANK { $name = "DENSE_RANK"; }
    | K_PERCENT_RANK { $name = "PERCENT_RANK"; }
    | K_NTILE { $name = "NTILE"; }
    | K_LAG { $name = "LAG"; }
    | K_LEAD { $name = "LEAD"; }
    | K_FIRST_VALUE { $name = "FIRST_VALUE"; }
    | K_LAST_VALUE { $name = "LAST_VALUE"; }
    | K_NTH_VALUE { $name = "NTH_VALUE"; }
    | K_CUME_DIST { $name = "CUME_DIST"; }
    ;
```

## 3. Reserved Keywords

Add the following to the reserved keywords list in `Parser.g`:

```java
private static final Set<String> RESERVED_KEYWORDS = new HashSet<String>()
{{
    // Existing keywords...

    // RFC-0008 additions
    add("over");
    add("window");
    add("rows");
    add("range");
    add("preceding");
    add("following");
    add("unbounded");
    add("recursive");
    add("filter");
    add("within");
    add("partition");
    add("row_number");
    add("rank");
    add("dense_rank");
    add("percent_rank");
    add("ntile");
    add("lag");
    add("lead");
    add("first_value");
    add("last_value");
    add("nth_value");
    add("cume_dist");
}};
```

## 4. Example Transformed Queries

### Example 1: Simple CTE
```sql
WITH recent_orders AS (
    SELECT user_id, order_id FROM orders WHERE date = '2024-11-15'
)
SELECT user_id, COUNT(*) FROM recent_orders GROUP BY user_id;
```

Parsed as:
- CTE: `recent_orders` with statement and no column aliases
- Main query: SELECT with GROUP BY

### Example 2: Window Function
```sql
SELECT
    id,
    value,
    ROW_NUMBER() OVER (ORDER BY value DESC) as rank
FROM table1;
```

Parsed as:
- Selector 1: `id`
- Selector 2: `value`
- Selector 3: Window function `ROW_NUMBER()` with OVER clause containing ORDER BY

### Example 3: Filtered Aggregate
```sql
SELECT
    category,
    COUNT(*) FILTER (WHERE active = true) as active_count,
    SUM(value) FILTER (WHERE value > 100) as large_sum
FROM products
GROUP BY category;
```

Parsed as:
- Aggregate `COUNT(*)` with FILTER clause `active = true`
- Aggregate `SUM(value)` with FILTER clause `value > 100`

## 5. Implementation Notes

1. **Backward Compatibility**: All new syntax is additive. Existing queries work unchanged.

2. **Parser Integration**: After grammar changes, regenerate parser with:
   ```bash
   ant generate-cql-grammar
   ```

3. **Testing**: The grammar changes must be tested with:
   - Valid syntax acceptance
   - Invalid syntax rejection
   - Error message clarity

4. **Performance**: Parser complexity increases slightly but impact is minimal since:
   - Optional clauses use efficient predicates
   - No backtracking in critical paths

## 6. Migration Steps

1. Add new keywords to `Lexer.g`
2. Add new grammar rules to `Parser.g`
3. Regenerate ANTLR parser
4. Update statement builders
5. Integrate with query processor
6. Test thoroughly

## 7. Future Extensions

Possible future enhancements:
- Named window definitions: `WINDOW w AS (...)`
- Window function aliases
- Additional statistical functions
- Cross-partition window functions (with limitations)
