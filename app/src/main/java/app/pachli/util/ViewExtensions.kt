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

package app.pachli.util

import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import timber.log.Timber

/**
 * Reduce ViewPager2's sensitivity to horizontal swipes.
 */
fun ViewPager2.reduceSwipeSensitivity() {
    // ViewPager2 is very sensitive to horizontal motion when swiping vertically, and will
    // trigger a page transition if the user's swipe is only a few tens of degrees off from
    // vertical. This is a problem if the underlying content is a list that the user wants
    // to scroll vertically -- it's far too easy to trigger an accidental horizontal swipe.
    //
    // One way to stop this is to reach in to ViewPager2's RecyclerView and adjust the amount
    // of touch slop it has.
    //
    // See https://issuetracker.google.com/issues/139867645 and
    // https://bladecoder.medium.com/fixing-recyclerview-nested-scrolling-in-opposite-direction-f587be5c1a04
    // for more (the approach in that Medium article works, but is still quite sensitive to
    // horizontal movement while scrolling).
    try {
        val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        recyclerViewField.isAccessible = true
        val recyclerView = recyclerViewField.get(this) as RecyclerView

        val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        touchSlopField.isAccessible = true
        val touchSlop = touchSlopField.get(recyclerView) as Int
        // Experimentally, 2 seems to be a sweet-spot, requiring a downward swipe that's at least
        // 45 degrees off the vertical to trigger a change. This is consistent with maximum angle
        // supported to open the nav. drawer.
        val scaleFactor = 2
        touchSlopField.set(recyclerView, touchSlop * scaleFactor)
    } catch (e: Exception) {
        Timber.tag("reduceSwipeSensitibity").w(e)
    }
}

/**
 * TextViews with an ancestor RecyclerView can forget that they are selectable. Toggling
 * calls to [TextView.setTextIsSelectable] fixes this.
 *
 * See [https://issuetracker.google.com/issues/37095917](https://issuetracker.google.com/issues/37095917)
 */
fun TextView.fixTextSelection() {
    setTextIsSelectable(false)
    post { setTextIsSelectable(true) }
}
