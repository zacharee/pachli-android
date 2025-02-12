/*
 * Copyright 2017 Andrew Dawson
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

package app.pachli

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.pachli.core.activity.BaseActivity
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.navigation.StatusListActivityIntent
import app.pachli.core.network.model.MastoList
import app.pachli.databinding.ActivityListsBinding
import app.pachli.databinding.DialogListBinding
import app.pachli.viewmodel.ListsViewModel
import app.pachli.viewmodel.ListsViewModel.Event
import app.pachli.viewmodel.ListsViewModel.LoadingState.ERROR_NETWORK
import app.pachli.viewmodel.ListsViewModel.LoadingState.ERROR_OTHER
import app.pachli.viewmodel.ListsViewModel.LoadingState.INITIAL
import app.pachli.viewmodel.ListsViewModel.LoadingState.LOADED
import app.pachli.viewmodel.ListsViewModel.LoadingState.LOADING
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ListsActivity : BaseActivity() {
    private val viewModel: ListsViewModel by viewModels()

    private val binding by viewBinding(ActivityListsBinding::inflate)

    private val adapter = ListsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.title_lists)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.listsRecycler.adapter = adapter
        binding.listsRecycler.layoutManager = LinearLayoutManager(this)
        binding.listsRecycler.addItemDecoration(
            MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL),
        )

        binding.swipeRefreshLayout.setOnRefreshListener { viewModel.retryLoading() }
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))

        lifecycleScope.launch {
            viewModel.state.collect(this@ListsActivity::update)
        }

        viewModel.retryLoading()

        binding.addListButton.setOnClickListener {
            showlistNameDialog(null)
        }

        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    Event.CREATE_ERROR -> showMessage(R.string.error_create_list)
                    Event.UPDATE_ERROR -> showMessage(R.string.error_rename_list)
                    Event.DELETE_ERROR -> showMessage(R.string.error_delete_list)
                }
            }
        }
    }

    private fun showlistNameDialog(list: MastoList?) {
        val binding = DialogListBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(binding.root)
            .setPositiveButton(
                if (list == null) {
                    R.string.action_create_list
                } else {
                    R.string.action_rename_list
                },
            ) { _, _ ->
                onPickedDialogName(binding.nameText.text.toString(), list?.id, binding.exclusiveCheckbox.isChecked)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        binding.nameText.let { editText ->
            editText.doOnTextChanged { s, _, _, _ ->
                dialog.getButton(Dialog.BUTTON_POSITIVE).isEnabled = s?.isNotBlank() == true
            }
            editText.setText(list?.title)
            editText.text?.let { editText.setSelection(it.length) }
        }

        list?.exclusive?.let {
            binding.exclusiveCheckbox.isChecked = isTaskRoot
        } ?: binding.exclusiveCheckbox.hide()
    }

    private fun showListDeleteDialog(list: MastoList) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.dialog_delete_list_warning, list.title))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteList(list.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun update(state: ListsViewModel.State) {
        adapter.submitList(state.lists)
        binding.progressBar.visible(state.loadingState == LOADING)
        binding.swipeRefreshLayout.isRefreshing = state.loadingState == LOADING
        when (state.loadingState) {
            INITIAL, LOADING -> binding.messageView.hide()
            ERROR_NETWORK -> {
                binding.messageView.show()
                binding.messageView.setup(R.drawable.errorphant_offline, R.string.error_network) {
                    viewModel.retryLoading()
                }
            }
            ERROR_OTHER -> {
                binding.messageView.show()
                binding.messageView.setup(R.drawable.errorphant_error, R.string.error_generic) {
                    viewModel.retryLoading()
                }
            }
            LOADED ->
                if (state.lists.isEmpty()) {
                    binding.messageView.show()
                    binding.messageView.setup(
                        R.drawable.elephant_friend_empty,
                        R.string.message_empty,
                        null,
                    )
                } else {
                    binding.messageView.hide()
                }
        }
    }

    private fun showMessage(@StringRes messageId: Int) {
        Snackbar.make(
            binding.listsRecycler,
            messageId,
            Snackbar.LENGTH_SHORT,
        ).show()
    }

    private fun onListSelected(listId: String, listTitle: String) {
        startActivityWithSlideInAnimation(
            StatusListActivityIntent.list(this, listId, listTitle),
        )
    }

    private fun openListSettings(list: MastoList) {
        AccountsInListFragment.newInstance(list.id, list.title).show(supportFragmentManager, null)
    }

    private fun renameListDialog(list: MastoList) {
        showlistNameDialog(list)
    }

    private fun onMore(list: MastoList, view: View) {
        PopupMenu(view.context, view).apply {
            inflate(R.menu.list_actions)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.list_edit -> openListSettings(list)
                    R.id.list_update -> renameListDialog(list)
                    R.id.list_delete -> showListDeleteDialog(list)
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    private object ListsDiffer : DiffUtil.ItemCallback<MastoList>() {
        override fun areItemsTheSame(oldItem: MastoList, newItem: MastoList): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MastoList, newItem: MastoList): Boolean {
            return oldItem == newItem
        }
    }

    private inner class ListsAdapter :
        ListAdapter<MastoList, ListsAdapter.ListViewHolder>(ListsDiffer) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
            return LayoutInflater.from(parent.context).inflate(R.layout.item_list, parent, false)
                .let(this::ListViewHolder)
                .apply {
                    val iconColor = MaterialColors.getColor(nameTextView, android.R.attr.textColorTertiary)
                    val context = nameTextView.context
                    val icon = IconicsDrawable(context, GoogleMaterial.Icon.gmd_list).apply {
                        sizeDp = 20
                        colorInt = iconColor
                    }

                    nameTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
                }
        }

        override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
            holder.nameTextView.text = getItem(position).title
        }

        private inner class ListViewHolder(view: View) :
            RecyclerView.ViewHolder(view),
            View.OnClickListener {
            val nameTextView: TextView = view.findViewById(R.id.list_name_textview)
            val moreButton: ImageButton = view.findViewById(R.id.editListButton)

            init {
                view.setOnClickListener(this)
                moreButton.setOnClickListener(this)
            }

            override fun onClick(v: View) {
                if (v == itemView) {
                    val list = getItem(bindingAdapterPosition)
                    onListSelected(list.id, list.title)
                } else {
                    onMore(getItem(bindingAdapterPosition), v)
                }
            }
        }
    }

    private fun onPickedDialogName(name: String, listId: String?, exclusive: Boolean) {
        if (listId == null) {
            viewModel.createNewList(name, exclusive)
        } else {
            viewModel.updateList(listId, name, exclusive)
        }
    }
}
