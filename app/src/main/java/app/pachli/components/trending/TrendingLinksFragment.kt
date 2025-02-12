/*
 * Copyright 2023 Pachli Association
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

package app.pachli.components.trending

import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import app.pachli.R
import app.pachli.components.trending.viewmodel.InfallibleUiAction
import app.pachli.components.trending.viewmodel.LoadState
import app.pachli.components.trending.viewmodel.TrendingLinksViewModel
import app.pachli.core.activity.openLink
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.designsystem.R as DR
import app.pachli.databinding.FragmentTrendingLinksBinding
import app.pachli.interfaces.ActionButtonActivity
import app.pachli.interfaces.AppBarLayoutHost
import app.pachli.interfaces.RefreshableFragment
import app.pachli.interfaces.ReselectableFragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber

@AndroidEntryPoint
class TrendingLinksFragment :
    Fragment(R.layout.fragment_trending_links),
    OnRefreshListener,
    ReselectableFragment,
    RefreshableFragment,
    MenuProvider {

    private val viewModel: TrendingLinksViewModel by viewModels()

    private val binding by viewBinding(FragmentTrendingLinksBinding::bind)

    private lateinit var adapter: TrendingLinksAdapter

    private var talkBackWasEnabled = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.recyclerView.layoutManager = getLayoutManager(
            requireContext().resources.getInteger(DR.integer.trending_column_count),
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        adapter = TrendingLinksAdapter(viewModel.statusDisplayOptions.value, ::onOpenLink)

        setupSwipeRefreshLayout()
        setupRecyclerView()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loadState.collectLatest {
                when (it) {
                    LoadState.Initial -> {
                        viewModel.accept(InfallibleUiAction.Reload)
                    }

                    LoadState.Loading -> {
                        if (!binding.swipeRefreshLayout.isRefreshing) {
                            binding.progressBar.show()
                        } else {
                            binding.progressBar.hide()
                        }
                    }

                    is LoadState.Success -> {
                        adapter.submitList(it.data)
                        binding.progressBar.hide()
                        binding.swipeRefreshLayout.isRefreshing = false
                        if (it.data.isEmpty()) {
                            binding.messageView.setup(
                                R.drawable.elephant_friend_empty,
                                R.string.message_empty,
                                null,
                            )
                            binding.messageView.show()
                        } else {
                            binding.messageView.hide()
                            binding.recyclerView.show()
                        }
                    }

                    is LoadState.Error -> {
                        binding.progressBar.hide()
                        binding.swipeRefreshLayout.isRefreshing = false
                        binding.recyclerView.hide()
                        if (adapter.itemCount != 0) {
                            val snackbar = Snackbar.make(
                                binding.root,
                                it.throwable.message ?: "Error",
                                Snackbar.LENGTH_INDEFINITE,
                            )

                            if (it.throwable !is HttpException || it.throwable.code() != 404) {
                                snackbar.setAction("Retry") { viewModel.accept(InfallibleUiAction.Reload) }
                            }
                            snackbar.show()
                        } else {
                            if (it.throwable !is HttpException || it.throwable.code() != 404) {
                                binding.messageView.setup(it.throwable) {
                                    viewModel.accept(InfallibleUiAction.Reload)
                                }
                            } else {
                                binding.messageView.setup(it.throwable)
                            }
                            binding.messageView.show()
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statusDisplayOptions.collectLatest {
                adapter.statusDisplayOptions = it
            }
        }

        (activity as? ActionButtonActivity)?.actionButton?.hide()
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager =
            getLayoutManager(requireContext().resources.getInteger(DR.integer.trending_column_count))
        binding.recyclerView.setHasFixedSize(true)
        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        binding.recyclerView.adapter = adapter
    }

    private fun getLayoutManager(columnCount: Int) = GridLayoutManager(context, columnCount)

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_trending_links, menu)
        menu.findItem(R.id.action_refresh)?.apply {
            icon = IconicsDrawable(requireContext(), GoogleMaterial.Icon.gmd_refresh).apply {
                sizeDp = 20
                colorInt =
                    MaterialColors.getColor(binding.root, android.R.attr.textColorPrimary)
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                refreshContent()
                true
            }

            else -> false
        }
    }

    override fun refreshContent() {
        binding.swipeRefreshLayout.isRefreshing = true
        onRefresh()
    }

    override fun onRefresh() = viewModel.accept(InfallibleUiAction.Reload)

    override fun onReselect() {
        if (isAdded) {
            binding.recyclerView.layoutManager?.scrollToPosition(0)
            binding.recyclerView.stopScroll()
        }
    }

    private fun onOpenLink(url: String) = requireContext().openLink(url)

    override fun onResume() {
        super.onResume()
        val a11yManager =
            ContextCompat.getSystemService(requireContext(), AccessibilityManager::class.java)

        val wasEnabled = talkBackWasEnabled
        talkBackWasEnabled = a11yManager?.isEnabled == true
        Timber.d("talkback was enabled: $wasEnabled, now $talkBackWasEnabled")
        if (talkBackWasEnabled && !wasEnabled) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }

        (requireActivity() as? AppBarLayoutHost)?.appBarLayout?.setLiftOnScrollTargetView(binding.recyclerView)
    }

    companion object {
        fun newInstance() = TrendingLinksFragment()
    }
}
