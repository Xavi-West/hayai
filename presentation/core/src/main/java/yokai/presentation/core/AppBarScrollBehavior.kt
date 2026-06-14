package yokai.presentation.core

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection

/**
 * Expose clean wrappers for standard Material 3 scroll behaviors to decouple presentation screens
 * from direct dependencies on Material 3 defaults and experimental annotations.
 */
class AppBarScrollBehavior @OptIn(ExperimentalMaterial3Api::class) constructor(
    m3ScrollBehavior: TopAppBarScrollBehavior
) {
    @ExperimentalMaterial3Api
    val m3ScrollBehavior: TopAppBarScrollBehavior = m3ScrollBehavior

    val nestedScrollConnection: NestedScrollConnection
        @OptIn(ExperimentalMaterial3Api::class)
        get() = m3ScrollBehavior.nestedScrollConnection
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun pinnedAppBarScrollBehavior(): AppBarScrollBehavior =
    AppBarScrollBehavior(TopAppBarDefaults.pinnedScrollBehavior())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun enterAlwaysAppBarScrollBehavior(
    canScroll: () -> Boolean = { true },
): AppBarScrollBehavior =
    AppBarScrollBehavior(
        TopAppBarDefaults.enterAlwaysScrollBehavior(
            state = rememberTopAppBarState(),
            canScroll = canScroll,
        )
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun enterAlwaysCollapsedAppBarScrollBehavior(
    canScroll: () -> Boolean = { true },
    isAtTop: () -> Boolean = { true },
): AppBarScrollBehavior {
    val state = rememberTopAppBarState()
    val atTop = isAtTop()
    val scrollable = canScroll()

    LaunchedEffect(atTop, scrollable) {
        if (atTop || !scrollable) {
            state.heightOffset = 0f
            state.contentOffset = 0f
        }
    }

    return AppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            state = state,
            canScroll = canScroll,
        )
    )
}
