package com.davy.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

/**
 * A Text composable that automatically detects URLs and makes them clickable.
 * URLs are displayed with underline and primary color, and open in browser when clicked.
 */
@Composable
fun LinkifyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    
    // Regex pattern to match URLs
    val urlPattern = remember {
        Regex(
            """(https?://[^\s]+|(?<![/@])(?:www\.)?(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}(?:/[^\s]*)?)""",
            RegexOption.IGNORE_CASE
        )
    }
    
    val annotatedString = remember(text, linkColor) {
        buildAnnotatedString {
            append(text)
            
            urlPattern.findAll(text).forEach { matchResult ->
                val start = matchResult.range.first
                val end = matchResult.range.last + 1
                val url = matchResult.value
                
                // Add styling for the link
                addStyle(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    ),
                    start = start,
                    end = end
                )
                
                // Add annotation to make it clickable
                addStringAnnotation(
                    tag = "URL",
                    annotation = if (url.startsWith("http")) url else "https://$url",
                    start = start,
                    end = end
                )
            }
        }
    }
    
    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                    context.startActivity(intent)
                }
        }
    )
}
