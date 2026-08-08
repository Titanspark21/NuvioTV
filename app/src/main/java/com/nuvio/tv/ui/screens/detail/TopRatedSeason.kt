package com.nuvio.tv.ui.screens.detail

/**
 * Fork addition.
 *
 * Sentinel used in place of a season number for the virtual "Top rated" tab, which lists
 * every episode of a show in one list ordered by rating instead of split by season.
 *
 * It is negative on purpose: real seasons are 0 (specials) or greater, so nothing upstream
 * can collide with it, and the season tab row can spot it with a simple `< 0` test. The
 * tab is resolved where the season state is handed down in MetaDetailsScreen, so the view
 * model is never asked to load it as if it were a real season.
 */
const val TOP_RATED_SEASON: Int = -1
