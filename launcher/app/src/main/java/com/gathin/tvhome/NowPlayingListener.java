package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Does nothing itself: the access granted to it (`cmd notification allow_listener
 * com.gathin.tvhome/.NowPlayingListener`) is what lets NowPlaying read other apps' media sessions.
 * Notifications are not read.
 */
public class NowPlayingListener extends NotificationListenerService {
}
