package eu.kanade.tachiyomi.ui.discover

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab

data object DiscoverTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(
                index = 5u,
                title = "Discover",
                icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Outlined.Home),
            )
        }

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { DiscoverScreenModel() }
        DiscoverContent(screenModel = screenModel)
    }
}
