package com.anydownlod.ui.i18n

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A user-facing string that stays unresolved until composition, so the same
 * state can follow the current locale.
 */
sealed interface UiText {
    data class Of(val resource: StringResource, val args: List<Any> = emptyList()) : UiText

    data class Quantity(
        val resource: PluralStringResource,
        val quantity: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Raw(val value: String) : UiText

    data class Combined(val parts: List<UiText>, val separator: String = " ") : UiText

    companion object {
        fun of(resource: StringResource, vararg args: Any): UiText = Of(resource, args.toList())

        fun quantity(resource: PluralStringResource, quantity: Int, vararg args: Any): UiText =
            Quantity(resource, quantity, args.toList())

        fun raw(value: String): UiText = Raw(value)

        fun combined(parts: List<UiText>, separator: String = " "): UiText = Combined(parts, separator)
    }
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Of -> if (args.isEmpty()) stringResource(resource) else stringResource(resource, *args.toTypedArray())
    is UiText.Quantity ->
        if (args.isEmpty()) {
            pluralStringResource(resource, quantity)
        } else {
            pluralStringResource(resource, quantity, *args.toTypedArray())
        }
    is UiText.Raw -> value
    // Resolve children first; joinToString's lambda is not a composable context.
    is UiText.Combined -> parts.map { it.resolve() }.joinToString(separator)
}

@Composable
fun text(resource: StringResource, vararg args: Any): String =
    if (args.isEmpty()) stringResource(resource) else stringResource(resource, *args)
