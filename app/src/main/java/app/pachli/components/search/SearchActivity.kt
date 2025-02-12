/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Pachli.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Pachli is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli.components.search

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import app.pachli.R
import app.pachli.components.search.adapter.SearchPagerAdapter
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.preferences.PrefKeys
import app.pachli.databinding.ActivitySearchBinding
import app.pachli.util.reduceSwipeSensitivity
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchActivity : BottomSheetActivity(), MenuProvider, SearchView.OnQueryTextListener {
    private val viewModel: SearchViewModel by viewModels()

    private val binding by viewBinding(ActivitySearchBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
        addMenuProvider(this)
        setupPages()
        handleIntent(intent)
    }

    private fun setupPages() {
        binding.pages.reduceSwipeSensitivity()
        binding.pages.adapter = SearchPagerAdapter(this)

        val enableSwipeForTabs = sharedPreferencesRepository.getBoolean(PrefKeys.ENABLE_SWIPE_FOR_TABS, true)
        binding.pages.isUserInputEnabled = enableSwipeForTabs

        TabLayoutMediator(binding.tabs, binding.pages) {
                tab, position ->
            tab.text = getPageTitle(position)
        }.attach()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateMenu(menu, menuInflater)
        menuInflater.inflate(R.menu.search_toolbar, menu)
        val searchViewMenuItem = menu.findItem(R.id.action_search)
        searchViewMenuItem.expandActionView()
        val searchView = searchViewMenuItem.actionView as SearchView
        setupSearchView(searchView)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        super.onMenuItemSelected(menuItem)
        return false
    }

    override fun finish() {
        super.finishWithoutSlideOutAnimation()
    }

    private fun getPageTitle(position: Int): CharSequence {
        return when (position) {
            0 -> getString(R.string.title_posts)
            1 -> getString(R.string.title_accounts)
            2 -> getString(R.string.title_hashtags_dialog)
            else -> throw IllegalArgumentException("Unknown page index: $position")
        }
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_SEARCH == intent.action) {
            viewModel.currentQuery = intent.getStringExtra(SearchManager.QUERY).orEmpty()
            viewModel.search(viewModel.currentQuery)
        }
    }

    private fun setupSearchView(searchView: SearchView) {
        searchView.setIconifiedByDefault(false)
        searchView.setSearchableInfo((getSystemService(Context.SEARCH_SERVICE) as? SearchManager)?.getSearchableInfo(componentName))

        // SearchView has a bug. If it's displayed 'app:showAsAction="always"' it's too wide,
        // pushing other icons (including the options menu '...' icon) off the edge of the
        // screen.
        //
        // E.g., see:
        //
        // - https://stackoverflow.com/questions/41662373/android-toolbar-searchview-too-wide-to-move-other-items
        // - https://stackoverflow.com/questions/51525088/how-to-control-size-of-a-searchview-in-toolbar
        // - https://stackoverflow.com/questions/36976163/push-icons-away-when-expandig-searchview-in-android-toolbar
        // - https://issuetracker.google.com/issues/36976484
        //
        // The fix is to use 'app:showAsAction="ifRoom|collapseActionView"' and then immediately
        // expand it after inflating. That sets the width correctly.
        //
        // But if you do that code in AppCompatDelegateImpl activates, and when the user presses
        // the "Back" button the SearchView is first set to its collapsed state. The user has to
        // press "Back" again to exit the activity. This is clearly unacceptable.
        //
        // It appears to be impossible to override this behaviour on API level < 33.
        //
        // SearchView does allow you to specify the maximum width. So take the screen width,
        // subtract 48dp * 2 (for the menu icon and back icon on either side), convert to pixels,
        // and use that.
        val pxScreenWidth = resources.displayMetrics.widthPixels
        val pxBuffer = ((48 * 2) * resources.displayMetrics.density).toInt()
        searchView.maxWidth = pxScreenWidth - pxBuffer

        // Keep text that was entered also when switching to a different tab (before the search is executed)
        searchView.setOnQueryTextListener(this)
        searchView.setQuery(viewModel.currentSearchFieldContent ?: "", false)

        searchView.requestFocus()
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        viewModel.currentSearchFieldContent = newText

        return false
    }
}
