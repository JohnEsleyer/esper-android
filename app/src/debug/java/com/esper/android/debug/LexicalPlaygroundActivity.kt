package com.esper.android.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.ui.lexical.ComposeLexicalEditor
import com.esper.android.ui.theme.EsperTheme

/**
 * Debug-only sandbox for the live Lexical renderer.
 *
 * Ad hoc device testing of the editor used to mean typing into a real synced
 * notebook, which then pushed throwaway edits to the server. This activity keeps
 * the markdown in `remember` state, so it renders every construct the parser
 * supports and can be poked at freely.
 *
 * Launch it with:
 * adb shell am start -n com.johnesleyer.esper/com.esper.android.debug.LexicalPlaygroundActivity
 */
class LexicalPlaygroundActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EsperTheme {
                Playground()
            }
        }
    }
}

@Composable
private fun Playground() {
    var markdown by remember { mutableStateOf(SampleMarkdown) }
    var showSource by remember { mutableStateOf(false) }
    // The editor owns its text after the first composition, so a reset has to
    // recreate it rather than hand it a new value.
    var resetKey by remember { mutableStateOf(0) }

    Column(modifier = Modifier.background(Color(0xFF0A0A0F))) {
        key(resetKey) {
            ComposeLexicalEditor(
                initialMarkdown = SampleMarkdown,
                onContentChanged = { markdown = it },
                onTimestampClicked = {},
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(color = Color(0xFF2A2A3A), thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = if (showSource) "Hide source" else "Show source",
                color = Color(0xFF818CF8),
                fontSize = 12.sp,
                modifier = Modifier.clickable { showSource = !showSource }
            )
            Text(
                text = "Reset",
                color = Color(0xFF818CF8),
                fontSize = 12.sp,
                modifier = Modifier.clickable {
                    markdown = SampleMarkdown
                    resetKey++
                }
            )
            Text(
                text = "${markdown.length} chars",
                color = Color(0xFF6B7280),
                fontSize = 12.sp
            )
        }

        if (showSource) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = markdown,
                    color = Color(0xFF9CA3AF),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Deliberately dense: every block type the parser knows about, plus the inline
 * cases that used to break (`:` in tables and URLs, timestamps, emoji, math).
 */
private val SampleMarkdown = """
# Heading One :sparkles:

## Heading Two

### Heading Three

#### Heading Four

##### Heading Five

###### Heading Six

A paragraph with **bold**, *italic*, ~~strike~~, `inline code`, a [web link](https://example.com), an [email link](mailto:hi@example.com), and a [phone link](tel:+15551234). Timestamps work too: [00:42] and [1:02:03].

This paragraph has soft line breaks
that continue on the next line
without starting a new paragraph.

This line ends with two spaces for a hard break  
and this is the next line.

> A quoted line.
> A second quoted line that continues.

- bullet one
- bullet two with **bold**
  - nested bullet

1. first item
2. second item

- [ ] unchecked task
- [x] checked task

| Left | Center | Right |
|:-----|:------:|------:|
| a    | b      | c     |
| 1    | 2      | 3     |

```kotlin
fun main() {
    println("hello")

    println("after a blank line")
}
```

Inline math ${'$'}E = mc^2${'$'} sits inside a sentence.

${'$'}${'$'}
\int_0^1 x^2 \, dx
${'$'}${'$'}

---

Final paragraph after a rule.
""".trimStart()
