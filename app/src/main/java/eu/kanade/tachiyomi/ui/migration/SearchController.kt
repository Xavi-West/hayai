package eu.kanade.tachiyomi.ui.migration

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import androidx.core.os.bundleOf
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.main.BottomNavBarInterface
import eu.kanade.tachiyomi.ui.migration.manga.process.MigrationListController
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchCardAdapter
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.util.view.searchToolbar
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import yokai.util.koin.get
import yokai.util.koin.injectLazy

class SearchController(
    private val originalTitle: String? = null,
    private val originalSource: Long = NO_SOURCE,
    private var sources: List<CatalogueSource>? = null,
) : GlobalSearchController(
    originalTitle,
    bundle = bundleOf(
        ORIGINAL_TITLE to originalTitle,
        ORIGINAL_SOURCE to originalSource,
        SOURCES to sources?.map { it.id }?.toLongArray(),
    ),
),
    BottomNavBarInterface {

    /**
     * Called when controller is initialized.
     */
    init {
        setHasOptionsMenu(true)
    }

    constructor(manga: Manga, sources: List<CatalogueSource>?) :
        this(
            originalTitle = manga.originalTitle,
            originalSource = manga.source,
            sources = sources,
        )

    @Suppress("unused")
    constructor(bundle: Bundle) : this(
        originalTitle = bundle.getString(ORIGINAL_TITLE),
        originalSource = bundle.getLong(ORIGINAL_SOURCE, NO_SOURCE),
        sources = restoreSources(bundle.getLongArray(SOURCES)),
    )

    override val presenter = SearchPresenter(initialQuery, originalSource, sources = sources)

    override fun onMangaClick(manga: Manga) {
        if (targetController is MigrationListController) {
            val migrationListController = targetController as? MigrationListController
            val sourceManager: SourceManager by injectLazy()
            val source = sourceManager.get(manga.source) ?: return
            migrationListController?.useMangaForMigration(manga, source)
            router.popCurrentController()
            return
        }
    }

    override fun onMangaLongClick(position: Int, adapter: GlobalSearchCardAdapter) {
        // Call parent's default click listener
        val manga = adapter.getItem(position)?.manga ?: return
        super.onMangaClick(manga)
    }

    /**
     * Adds items to the options menu.
     *
     * @param menu menu containing options.
     * @param inflater used to load the menu xml.
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate menu.
        inflater.inflate(R.menu.catalogue_new_list, menu)

        setOnQueryTextChangeListener(searchToolbar()?.searchView, onlyOnSubmit = true, hideKbOnSubmit = true) {
            presenter.search(it ?: "")
            setTitle() // Update toolbar title
            true
        }
    }

    override fun canChangeTabs(block: () -> Unit): Boolean {
        val migrationListController = router.getControllerWithTag(MigrationListController.TAG)
            as? BottomNavBarInterface
        if (migrationListController != null) return migrationListController.canChangeTabs(block)
        return true
    }

    companion object {
        private const val ORIGINAL_TITLE = "original_title"
        private const val ORIGINAL_SOURCE = "original_source"
        const val SOURCES = "sources"
        private const val NO_SOURCE = -1L
    }
}

private fun restoreSources(sourceIds: LongArray?): List<CatalogueSource> {
    if (sourceIds == null) return emptyList()
    val sourceManager = get<SourceManager>()
    return buildList(sourceIds.size) {
        for (sourceId in sourceIds) {
            (sourceManager.get(sourceId) as? CatalogueSource)?.let(::add)
        }
    }
}
