package com.umuieme.videowebbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.umuieme.videowebbrowser.R
import com.umuieme.videowebbrowser.browser.UrlError
import com.umuieme.videowebbrowser.ui.theme.VideoWebBrowserTheme
import kotlinx.coroutines.flow.drop

/**
 * Asks for the site URL: on first run, and from "Change site". Fully usable with arrows, OK
 * and Back.
 *
 * @param error validation error from the last submission, or null.
 * @param onSubmit called with the raw text; the ViewModel validates and saves it.
 * @param onInputChanged called when the text changes, so a stale error can be cleared.
 * @param initialUrl prefills the field (the saved URL when changing site).
 * @param onCancel when non-null, Back calls it (return to the browser). On first run it's null
 *   and Back falls through to the Activity's exit handling.
 */
@Composable
fun SetupScreen(
    error: UrlError?,
    onSubmit: (String) -> Unit,
    onInputChanged: () -> Unit,
    modifier: Modifier = Modifier,
    initialUrl: String? = null,
    onCancel: (() -> Unit)? = null,
) {
    val urlState = rememberTextFieldState(initialText = initialUrl.orEmpty())
    val fieldFocus = remember { FocusRequester() }

    // Composed before TvUrlField, so its own BackHandler (leave the field while editing) is
    // registered later and wins: Back first stops editing, the next Back cancels.
    BackHandler(enabled = onCancel != null) { onCancel?.invoke() }

    // Nothing is focused when a screen appears. Focus the URL field so OK works immediately.
    LaunchedEffect(Unit) { fieldFocus.requestFocus() }

    // Clear the error as soon as the user edits the text (drop(1) skips the initial value).
    LaunchedEffect(urlState) {
        snapshotFlow { urlState.text.toString() }.drop(1).collect { onInputChanged() }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Shift content up when the on-screen keyboard covers the bottom of the screen.
                .imePadding()
                // TV overscan-safe margins.
                .padding(horizontal = 58.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.setup_explanation),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.setup_url_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TvUrlField(
                    state = urlState,
                    focusRequester = fieldFocus,
                    onSubmit = { onSubmit(urlState.text.toString()) },
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(error.messageRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = { onSubmit(urlState.text.toString()) }) {
                    Text(stringResource(R.string.setup_open))
                }
            }
        }
    }
}

/**
 * A text field that behaves well with a D-pad.
 *
 * Two focus targets: an outer "container" the remote moves between, and the real text field
 * inside, which is only focusable while editing. So moving focus onto the field never opens the
 * keyboard on its own (the remote can't get trapped); OK opens it; the IME action submits.
 */
@Composable
private fun TvUrlField(
    state: TextFieldState,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val textFocus = remember { FocusRequester() }
    var editing by remember { mutableStateOf(false) }
    var containerFocused by remember { mutableStateOf(false) }
    // Ignores the "unfocused" callback the text field gets before it was ever focused.
    var textHadFocus by remember { mutableStateOf(false) }

    fun stopEditing() {
        keyboard?.hide()
        editing = false
        focusRequester.requestFocus()
    }

    // OK on the container: let the text field take focus, then open the keyboard.
    LaunchedEffect(editing) {
        if (editing) {
            textFocus.requestFocus()
            keyboard?.show()
        }
    }

    // While editing with the keyboard closed, Back leaves the field instead of exiting the app.
    // (While the keyboard is open, the IME consumes Back itself to close.)
    BackHandler(enabled = editing) { stopEditing() }

    val highlighted = containerFocused || editing
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { containerFocused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // The border below is the focus indication.
            ) { editing = true }
            .background(if (highlighted) colors.surfaceVariant else colors.surface, shape)
            .border(
                width = if (highlighted) 3.dp else 1.dp,
                color = if (highlighted) colors.primary else colors.border,
                shape = shape,
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        BasicTextField(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(textFocus)
                .focusProperties { canFocus = editing }
                .onFocusChanged {
                    if (it.isFocused) {
                        textHadFocus = true
                    } else if (textHadFocus) {
                        // Focus left the field (e.g. Down to the button): stop editing.
                        textHadFocus = false
                        editing = false
                    }
                }
                .onPreviewKeyEvent { event ->
                    when (event.key) {
                        // OK while the keyboard is closed: reopen it. Consume both down and up.
                        Key.DirectionCenter -> {
                            if (event.type == KeyEventType.KeyDown) keyboard?.show()
                            true
                        }
                        // Up/Down leave the field instead of being swallowed by the text input.
                        // Left/Right fall through so they still move the text cursor.
                        Key.DirectionDown -> {
                            if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        Key.DirectionUp -> true
                        else -> false
                    }
                },
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                // URL keyboard layout: no spaces, quick "/" and ".com" keys on most TV IMEs.
                keyboardType = KeyboardType.Uri,
                // Shows "Go" on the action key. Some TV keyboards show "Done" regardless;
                // onKeyboardAction fires for whichever action key is pressed.
                imeAction = ImeAction.Go,
            ),
            onKeyboardAction = {
                stopEditing()
                onSubmit()
            },
            decorator = { innerTextField ->
                Box {
                    if (state.text.isEmpty()) {
                        Text(
                            text = stringResource(
                                if (containerFocused) R.string.setup_url_hint else R.string.setup_url_placeholder,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

private fun UrlError.messageRes(): Int = when (this) {
    UrlError.BLANK -> R.string.setup_error_blank
    UrlError.INVALID -> R.string.setup_error_invalid
    UrlError.UNSUPPORTED_SCHEME -> R.string.setup_error_unsupported_scheme
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun SetupScreenPreview() {
    VideoWebBrowserTheme {
        SetupScreen(error = UrlError.INVALID, onSubmit = {}, onInputChanged = {})
    }
}
