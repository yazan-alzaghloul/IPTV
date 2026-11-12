package com.iptv.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable fun sectionTitle(section: Section): String = stringResource(when (section) {
    Section.HOME -> R.string.ui_home
    Section.LIVE -> R.string.ui_live_tv
    Section.MOVIES -> R.string.ui_movies
    Section.SERIES -> R.string.ui_series
    Section.FAVORITES -> R.string.ui_watchlist
    Section.SEARCH -> R.string.ui_search
    Section.SETTINGS -> R.string.ui_settings
})

// The existing query and preference layers return English UI labels. Resolve those labels
// at the presentation boundary, while leaving provider names and artwork metadata intact.
@Composable fun uiLabel(value: String): String = localizedUiLabel(androidx.compose.ui.platform.LocalContext.current, value)

fun localizedUiLabel(context: android.content.Context, value: String): String {
    if (value.startsWith("Provider returned HTTP ") && value.endsWith(". Try again later."))
        return context.getString(R.string.provider_http_error, value.removePrefix("Provider returned HTTP ").removeSuffix(". Try again later."))
    if (value.startsWith("Subscription is ") && value.endsWith(". Contact your provider."))
        return context.getString(R.string.subscription_state, value.removePrefix("Subscription is ").removeSuffix(". Contact your provider."))
    val resource = when (value) {
        "Home" -> R.string.ui_home
        "Live TV" -> R.string.ui_live_tv
        "Movies" -> R.string.ui_movies
        "Series" -> R.string.ui_series
        "Watchlist" -> R.string.ui_watchlist
        "Search" -> R.string.ui_search
        "Settings" -> R.string.ui_settings
        "More" -> R.string.ui_more
        "Sign out?" -> R.string.ui_sign_out_question
        "This removes your saved login, favorites, history, and playback progress from this device." -> R.string.ui_this_removes_your_saved_login_favorites_history_and_playback_progress_from_this_device
        "Sign out" -> R.string.ui_sign_out
        "Cancel" -> R.string.ui_cancel
        "YOUR LIBRARY · YOUR WAY" -> R.string.ui_your_library_your_way
        "Refresh library" -> R.string.ui_refresh_library
        "FEATURED IN YOUR LIBRARY" -> R.string.ui_featured_in_your_library
        "More details" -> R.string.ui_more_details
        "Clear" -> R.string.ui_clear
        "Voice" -> R.string.ui_voice
        "All categories" -> R.string.ui_all_categories
        "Refresh" -> R.string.ui_refresh
        "LIVE" -> R.string.ui_live
        "Try again" -> R.string.ui_try_again
        "Watch history" -> R.string.ui_watch_history
        "Previous featured" -> R.string.ui_previous_featured
        "Next featured" -> R.string.ui_next_featured
        "Try another filter or refresh your provider." -> R.string.ui_try_another_filter_or_refresh_your_provider
        "Previous page" -> R.string.ui_previous_page
        "Next page" -> R.string.ui_next_page
        "All" -> R.string.ui_all
        "Your watch history is empty." -> R.string.ui_your_watch_history_is_empty
        "Remove history & progress" -> R.string.ui_remove_history_progress
        "Close" -> R.string.ui_close
        "Clear history" -> R.string.ui_clear_history
        "IPTV" -> R.string.ui_iptv
        "Your channels. One place." -> R.string.ui_your_channels_one_place
        "Sign in with your IPTV provider’s Xtream account." -> R.string.ui_sign_in_with_your_iptv_provider_s_xtream_account
        "HTTPS" -> R.string.ui_https
        "HTTP" -> R.string.ui_http
        "HTTP sends your login and video without encryption. Use HTTPS when your provider supports it." -> R.string.ui_http_sends_your_login_and_video_without_encryption_use_https_when_your_provider_supports_it
        "Host or provider URL" -> R.string.ui_host_or_provider_url
        "provider.example or https://provider.example/path/" -> R.string.ui_provider_example_or_https_provider_example_path
        "Port (optional)" -> R.string.ui_port_optional
        "Leave blank to use the URL port or HTTP/HTTPS default." -> R.string.ui_leave_blank_to_use_the_url_port_or_http_https_default
        "Username" -> R.string.ui_username
        "Password" -> R.string.ui_password
        "Your login stays on this device. Channels and titles come from your subscription." -> R.string.ui_your_login_stays_on_this_device_channels_and_titles_come_from_your_subscription
        "Restart from beginning" -> R.string.ui_restart_from_beginning
        "Retry details" -> R.string.ui_retry_details
        "On this channel" -> R.string.ui_on_this_channel
        "No episodes available from this provider." -> R.string.ui_no_episodes_available_from_this_provider
        "Restart movie" -> R.string.ui_restart_movie
        "Watch trailer" -> R.string.ui_watch_trailer
        "Autoplay next episode" -> R.string.ui_autoplay_next_episode
        "Watched" -> R.string.ui_watched
        "From beginning" -> R.string.ui_from_beginning
        "Clear watch history?" -> R.string.ui_clear_watch_history_question
        "This also resets saved progress for titles in your recent history. Favorites stay saved." -> R.string.ui_this_also_resets_saved_progress_for_titles_in_your_recent_history_favorites_stay_saved
        "Your device’s system text size is also respected." -> R.string.ui_your_device_s_system_text_size_is_also_respected
        "Clear watch history" -> R.string.ui_clear_watch_history
        "Clear artwork cache" -> R.string.ui_clear_artwork_cache
        "Export appearance" -> R.string.ui_export_appearance
        "Import appearance" -> R.string.ui_import_appearance
        "Reset appearance" -> R.string.ui_reset_appearance
        "CHANNEL GROUPS" -> R.string.ui_channel_groups
        "Search channels" -> R.string.ui_search_channels
        "All channels" -> R.string.ui_all_channels
        "Favorites" -> R.string.ui_favorites
        "Channels" -> R.string.ui_channels
        "Program guide" -> R.string.ui_program_guide
        "Choose a channel to see its schedule." -> R.string.ui_choose_a_channel_to_see_its_schedule
        "Preview unavailable. Try fullscreen playback." -> R.string.ui_preview_unavailable_try_fullscreen_playback
        "Full screen" -> R.string.ui_full_screen
        "No schedule supplied for this channel. You can still watch live." -> R.string.ui_no_schedule_supplied_for_this_channel_you_can_still_watch_live
        "Jump to now" -> R.string.ui_jump_to_now
        "Start preview" -> R.string.ui_start_preview
        "Stop preview" -> R.string.ui_stop_preview
        "Now" -> R.string.ui_now
        "Next" -> R.string.ui_next
        "Your next great watch" -> R.string.ui_your_next_great_watch
        "My watchlist" -> R.string.ui_my_watchlist
        "Search library" -> R.string.ui_search_library
        "More options" -> R.string.ui_more_options
        "Add to watchlist" -> R.string.ui_add_to_watchlist
        "Remove from watchlist" -> R.string.ui_remove_from_watchlist
        "Add to favorites" -> R.string.ui_add_to_favorites
        "Remove from favorites" -> R.string.ui_remove_from_favorites
        "Play now" -> R.string.ui_play_now
        "Play movie" -> R.string.ui_play_movie
        "Resume watching" -> R.string.ui_resume_watching
        "View episodes" -> R.string.ui_view_episodes
        "Continue series" -> R.string.ui_continue_series
        "Watch now" -> R.string.ui_watch_now
        "Play episode" -> R.string.ui_play_episode
        "Resume episode" -> R.string.ui_resume_episode
        "Mark watched" -> R.string.ui_mark_watched
        "Mark unwatched" -> R.string.ui_mark_unwatched
        "Mark season watched" -> R.string.ui_mark_season_watched
        "Mark season unwatched" -> R.string.ui_mark_season_unwatched
        "Description unavailable." -> R.string.ui_description_unavailable
        "Connect" -> R.string.ui_connect
        "Connecting…" -> R.string.ui_connecting
        "Show" -> R.string.ui_show
        "Hide" -> R.string.ui_hide
        "Watch live" -> R.string.ui_watch_live
        "No matching titles" -> R.string.ui_no_matching_titles
        "No titles available" -> R.string.ui_no_titles_available
        "Keep your favorites here" -> R.string.ui_keep_your_favorites_here
        "Try another search or category." -> R.string.ui_try_another_search_or_category
        "Search your library" -> R.string.ui_search_your_library
        "Appearance" -> R.string.ui_appearance
        "Make the library comfortable on your screen." -> R.string.ui_make_the_library_comfortable_on_your_screen
        "Theme" -> R.string.ui_theme
        "Cinematic dark" -> R.string.ui_cinematic_dark
        "Light" -> R.string.ui_light
        "Accent" -> R.string.ui_accent
        "Violet" -> R.string.ui_violet
        "Ocean" -> R.string.ui_ocean
        "High contrast" -> R.string.ui_high_contrast
        "Increase contrast for secondary text." -> R.string.ui_increase_contrast_for_secondary_text
        "Show the sections you use most." -> R.string.ui_show_the_sections_you_use_most
        "Open app to" -> R.string.ui_open_app_to
        "Optional motion" -> R.string.ui_optional_motion
        "Allow artwork rotation. System reduced-motion preferences take priority." -> R.string.ui_allow_artwork_rotation_system_reduced_motion_preferences_take_priority
        "Rotate featured artwork" -> R.string.ui_rotate_featured_artwork
        "Changes artwork every 12 seconds while the hero is not focused. No video autoplay." -> R.string.ui_changes_artwork_every_12_seconds_while_the_hero_is_not_focused_no_video_autoplay
        "Show on Home when content is available." -> R.string.ui_show_on_home_when_content_is_available
        "Movies & episodes" -> R.string.ui_movies_episodes
        "Playback completion and autoplay are stored on this device." -> R.string.ui_playback_completion_and_autoplay_are_stored_on_this_device
        "Play the next provider episode after completion." -> R.string.ui_play_the_next_provider_episode_after_completion
        "Mark complete at" -> R.string.ui_mark_complete_at
        "Data & privacy" -> R.string.ui_data_privacy
        "Your account and viewing history stay on this device." -> R.string.ui_your_account_and_viewing_history_stay_on_this_device
        "Cast" -> R.string.ui_cast
        "Director" -> R.string.ui_director
        "Provider languages" -> R.string.ui_provider_languages
        "Provider subtitles" -> R.string.ui_provider_subtitles
        "Back" -> R.string.ui_back
        "Aspect" -> R.string.ui_aspect
        "Quality" -> R.string.ui_quality
        "Info" -> R.string.ui_info
        "Picture-in-picture" -> R.string.ui_picture_in_picture
        "Previous episode" -> R.string.ui_previous_episode
        "Next episode" -> R.string.ui_next_episode
        "Episodes" -> R.string.ui_episodes
        "Channel −" -> R.string.ui_channel_previous
        "Channel +" -> R.string.ui_channel_next
        "Previous channel" -> R.string.ui_previous_channel
        "Retry playback" -> R.string.ui_retry_playback
        "Switch HLS / MPEG-TS" -> R.string.ui_switch_hls_mpeg_ts
        "Back to details" -> R.string.ui_back_to_details
        "Continue watching" -> R.string.ui_continue_watching
        "Continue watching series" -> R.string.ui_continue_watching_series
        "Recently watched channels" -> R.string.ui_recently_watched_channels
        "Recently added movies" -> R.string.ui_recently_added_movies
        "Featured movies" -> R.string.ui_featured_movies
        "Featured series" -> R.string.ui_featured_series
        "Recently added" -> R.string.ui_recently_added
        "Recommended from your viewing" -> R.string.ui_recommended_from_your_viewing
        "Top rated by your provider" -> R.string.ui_top_rated_by_your_provider
        "Provider order" -> R.string.ui_provider_order
        "Name A–Z" -> R.string.ui_name_a_z
        "Provider rating" -> R.string.ui_provider_rating
        "Title A–Z" -> R.string.ui_title_a_z
        "Release date" -> R.string.ui_release_date
        "Date added" -> R.string.ui_date_added
        "Category" -> R.string.ui_category
        "Genre" -> R.string.ui_genre
        "Year" -> R.string.ui_year
        "Language" -> R.string.ui_language
        "Sort" -> R.string.ui_sort
        "Live channel" -> R.string.ui_live_channel
        "Program information unavailable" -> R.string.ui_program_information_unavailable
        "Search history" -> R.string.ui_search_history
        "Streaming information" -> R.string.ui_streaming_information
        "Hide streaming information" -> R.string.ui_hide_streaming_information
        "No favorite channels yet. Save channels with the heart button." -> R.string.ui_no_favorite_channels_yet_save_channels_with_the_heart_button
        "No channels match. Try another category or search." -> R.string.ui_no_channels_match_try_another_category_or_search
        "Your library is ready" -> R.string.ui_your_library_is_ready
        "Refresh to load content from your provider." -> R.string.ui_refresh_to_load_content_from_your_provider
        "Open a title and add it to favorites. Long-press a card to save it quickly." -> R.string.ui_open_a_title_and_add_it_to_favorites_long_press_a_card_to_save_it_quickly
        "Favorite" -> R.string.ui_favorite
        "Grid view" -> R.string.ui_grid_view
        "List view" -> R.string.ui_list_view
        "Clear search" -> R.string.ui_clear_search
        "Refresh channels" -> R.string.ui_refresh_channels
        "Loading channels" -> R.string.ui_loading_channels
        "Loading library" -> R.string.ui_loading_library
        "Saved login could not be unlocked. Please sign in again." -> R.string.ui_saved_login_could_not_be_unlocked_please_sign_in_again
        "Could not clear saved data. Try signing out again." -> R.string.ui_could_not_clear_saved_data_try_signing_out_again
        "Check your details and try again." -> R.string.ui_check_your_details_and_try_again
        "Could not complete this request. Check your connection and try again." -> R.string.ui_could_not_complete_this_request_check_your_connection_and_try_again
        "Enter your username and password." -> R.string.ui_enter_your_username_and_password
        "Choose HTTP or HTTPS." -> R.string.ui_choose_http_or_https
        "Enter the provider host or URL." -> R.string.ui_enter_the_provider_host_or_url
        "Enter a valid HTTP or HTTPS provider URL." -> R.string.ui_enter_a_valid_http_or_https_provider_url
        "Use the provider base URL without login details, a query, or a fragment." -> R.string.ui_use_the_provider_base_url_without_login_details_a_query_or_a_fragment
        "Enter the base URL, not a playlist or player_api.php URL." -> R.string.ui_enter_the_base_url_not_a_playlist_or_player_api_php_url
        "Port must be a number between 1 and 65535." -> R.string.ui_port_must_be_a_number_between_1_and_65535
        "Cannot reach the provider. Check the address, connection, and HTTP/HTTPS setting." -> R.string.ui_cannot_reach_the_provider_check_the_address_connection_and_http_https_setting
        "Access denied. Check your login and subscription." -> R.string.ui_access_denied_check_your_login_and_subscription
        "Provider API not found. Check the host, port, and base path." -> R.string.ui_provider_api_not_found_check_the_host_port_and_base_path
        "The provider returned an empty response." -> R.string.ui_the_provider_returned_an_empty_response
        "The provider connection was interrupted. Try again." -> R.string.ui_the_provider_connection_was_interrupted_try_again
        "The provider returned an unreadable response. Check the base URL and try again." -> R.string.ui_the_provider_returned_an_unreadable_response_check_the_base_url_and_try_again
        "This address did not return a valid Xtream account." -> R.string.ui_this_address_did_not_return_a_valid_xtream_account
        "Login failed. Check your username and password." -> R.string.ui_login_failed_check_your_username_and_password
        "Your subscription has expired. Contact your provider." -> R.string.ui_your_subscription_has_expired_contact_your_provider
        "The provider could not load this catalog. Your session may have expired; retry or sign in again." -> R.string.ui_the_provider_could_not_load_this_catalog_your_session_may_have_expired_retry_or_sign_in_again
        "Details are unavailable. Try again later." -> R.string.ui_details_are_unavailable_try_again_later
        "Appearance exported. Provider credentials are excluded." -> R.string.ui_appearance_exported_provider_credentials_are_excluded
        "Could not export settings. Try another location." -> R.string.ui_could_not_export_settings_try_another_location
        "Appearance imported." -> R.string.ui_appearance_imported
        "This is not a supported appearance settings file." -> R.string.ui_this_is_not_a_supported_appearance_settings_file
        "Watch history cleared." -> R.string.ui_watch_history_cleared
        "Artwork cache cleared." -> R.string.ui_artwork_cache_cleared
        "Appearance reset." -> R.string.ui_appearance_reset
        "Connection interrupted. Check your network, then retry." -> R.string.ui_connection_interrupted_check_your_network_then_retry
        "The provider refused this stream. Check your subscription or try again later." -> R.string.ui_the_provider_refused_this_stream_check_your_subscription_or_try_again_later
        "This device cannot decode this stream. Try another title or audio track." -> R.string.ui_this_device_cannot_decode_this_stream_try_another_title_or_audio_track
        "This stream requires a supported DRM license configuration." -> R.string.ui_this_stream_requires_a_supported_drm_license_configuration
        "This stream is unavailable or unsupported. Retry or choose another title." -> R.string.ui_this_stream_is_unavailable_or_unsupported_retry_or_choose_another_title
        "Picture-in-picture is unavailable on this device." -> R.string.ui_picture_in_picture_is_unavailable_on_this_device
        "No app is available to open this trailer." -> R.string.ui_no_app_is_available_to_open_this_trailer
        "Could not prepare playback. Try again." -> R.string.ui_could_not_prepare_playback_try_again
        "Aspect ratio" -> R.string.ui_aspect_ratio
        "Fit" -> R.string.ui_fit
        "Fill" -> R.string.ui_fill
        "Zoom" -> R.string.ui_zoom
        "Playback quality" -> R.string.ui_playback_quality
        "Automatic" -> R.string.ui_automatic
        "Playback diagnostics" -> R.string.ui_playback_diagnostics
        "About & diagnostics" -> R.string.ui_about_diagnostics
        else -> return value
    }
    return context.getString(resource)
}
