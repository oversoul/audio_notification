package net.aecodes;

import android.app.NotificationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.graphics.BitmapFactory;
import android.support.v4.util.LruCache;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;

import com.backgroundaudio.R;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator;

import java.net.URL;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.io.IOException;
import java.util.ArrayList;



public class AudioPlayer extends Service {

    public static final String ACTION_NEXT = "action.NEXT";
    public static final String ACTION_PREV = "action.PREV";
    public static final String ACTION_PLAY = "action.PLAY";
    public static final String ACTION_STOP = "action.STOP";
    public static final String ACTION_SELECT = "action.SELECT";
    public static final String ACTION_TOGGLE = "action.TOGGLE";
    public static final String SERVICE_EVENT = "AudioPlayerServiceEvent";

    public static boolean play = false;
    public static boolean prepared = false;

    public static int index = 0;
    public static List<Map> customOptions = new ArrayList<Map>();
    private static Map<String, String> metadata = new HashMap<String, String>();
    private static List<Map<String, String>> songs = new ArrayList<Map<String, String>>();

    public static boolean repeat = false;
    public static boolean shuffle = false;

    private static SimpleExoPlayer player;
    private static DefaultDataSourceFactory dataSourceFactory;
    private PlayerNotificationManager playerNotificationManager;

    private MediaSessionCompat mediaSession;
    private LruCache<String, Bitmap> memoryCache;
    private MediaSessionConnector mediaSessionConnector;

    public static final int PLAYBACK_NOTIFICATION_ID = 1;
    public static final String PLAYBACK_CHANNEL_ID = "playback_channel";


    private void createCache() {
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;

        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            memoryCache.put(key, bitmap);
        }
    }

    public Bitmap getBitmapFromMemCache(String key) {
        return memoryCache.get(key);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        final Context context = getApplication().getApplicationContext();
        createCache();

        String agent = Util.getUserAgent(this, "audio-player");
        ComponentName receiver = new ComponentName(getPackageName(), RemoteReceiver.class.getName());

        player = ExoPlayerFactory.newSimpleInstance(this, new DefaultTrackSelector());
        player.addListener(new Player.DefaultEventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                callEvent(playWhenReady ? "play" : "pause");
                if ( Player.STATE_ENDED == playbackState ) {
                    onCompletion();
                }
            }
        });

        dataSourceFactory = new DefaultDataSourceFactory(this, agent);
        playerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(
            this,
            PLAYBACK_CHANNEL_ID,
            R.string.playback_channel_name,
            PLAYBACK_NOTIFICATION_ID,
            new PlayerNotificationManager.MediaDescriptionAdapter() {
                @Override
                public String getCurrentContentTitle(Player player) {
                    return getSong().get("title");
                }

                @Nullable
                @Override
                public PendingIntent createCurrentContentIntent(Player player) {
                    Intent intent = new Intent(context, getMainActivityClass(context));
                    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                }

                @Nullable
                @Override
                public String getCurrentContentText(Player player) {
                    return getSong().get("author");
                }

                @Nullable
                @Override
                public Bitmap getCurrentLargeIcon(Player player, final PlayerNotificationManager.BitmapCallback callback) {
                    final String image = getSong().get("image");
                    final String id = getSong().get("id");

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Bitmap oldImg = getBitmapFromMemCache(id);
                            if ( oldImg != null ) {
                                callback.onBitmap(oldImg);
                                return;
                            }
                            try {
                                URL url = new URL(image);
                                Bitmap img = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                                addBitmapToMemoryCache(id, img);
                                callback.onBitmap(img);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                    return null;
                }
            }
        );

        playerNotificationManager.setUseNavigationActions(false);

        // omit rewind action by setting the increment to zero
        playerNotificationManager.setFastForwardIncrementMs(0);
        playerNotificationManager.setRewindIncrementMs(0);

        playerNotificationManager.setControlDispatcher(new DefaultControlDispatcher() {
            @Override
            public boolean dispatchStop(Player player, boolean reset) {
                callEvent("stop");
                return false;
            }
        });

        playerNotificationManager.setNotificationListener(new PlayerNotificationManager.NotificationListener() {
            @Override
            public void onNotificationStarted(int notificationId, Notification notification) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    NotificationManager mNM = getSystemService(NotificationManager.class);
                    mNM.notify(notificationId, notification);
                } else {
                    startForeground(notificationId, notification);
                }
            }

            @Override
            public void onNotificationCancelled(int notificationId) {
                stopSelf();
            }
        });

        playerNotificationManager.setPlayer(player);

        mediaSession = new MediaSessionCompat(context, "media_session", receiver, null);
        mediaSession.setActive(true);

        playerNotificationManager.setMediaSessionToken(mediaSession.getSessionToken());
        mediaSessionConnector = new MediaSessionConnector(mediaSession);
        mediaSessionConnector.setQueueNavigator(new TimelineQueueNavigator(mediaSession) {
            @Override
            public MediaDescriptionCompat getMediaDescription(Player player, int windowIndex) {
                return internalGetMediaDescription();
            }
        });
        mediaSessionConnector.setPlayer(player, null);
    }

    private MediaDescriptionCompat internalGetMediaDescription() {
        Bundle extras = new Bundle();
        Bitmap img = getBitmapFromMemCache(getSong().get("id"));
        extras.putParcelable(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, img);
        extras.putParcelable(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, img);

        return new MediaDescriptionCompat.Builder()
            .setMediaId(String.valueOf(index))
            .setTitle(getSong().get("title"))
            .setDescription(getSong().get("author"))
            .setExtras(extras)
            .setIconBitmap(img)
            .build();
    }

    private static Class getMainActivityClass(Context context) {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }


    public void onCompletion() {
        if (player.getCurrentPosition() > 0) {
            player.seekTo(0);
            player.setPlayWhenReady(false);
//            callEvent("pause");
        }
    }

    @Override
    public void onDestroy() {
        mediaSession.release();
        mediaSessionConnector.setPlayer(null, null);
        playerNotificationManager.setPlayer(null);
        memoryCache.evictAll();
        player.release();
        player = null;

        super.onDestroy();
    }

    public static void setCustomOption(HashMap option) {
        for (Map map: AudioPlayer.customOptions) {
            if(map.get("name").equals(option.get("name"))) {
                map.clear();
                map.put("name", option.get("name"));
                map.put("value", option.get("value"));
                return;
            }
        }
        customOptions.add(option);
    }

    public static void setPlaylist(HashMap p) {
        if (p != null) {
            songs = (List<Map<String, String>>)p.get("songs");
            metadata = (Map<String, String>)p.get("metadata");
        }
    }

    @Nullable
    public static Map getPlaylist() {
        if (player == null || songs.size() == 0) {
            return null;
        }

        Map playlist = new HashMap<String, String>();
        playlist.put("songs", songs);
        playlist.put("metadata", metadata);

        playlist.put("index", index);
        playlist.put("playing", play);

        return playlist;
    }

    public static Map<String, String> getSong() {

        return songs.get(index);
    }

    public static long getPosition() {
        if (player != null) {
            return player.getCurrentPosition();
        }
        return 0;
    }

    public static long getDuration() {
        if (player != null) {
            long duration = player.getDuration();
            return duration < 0 ? 0 : duration;
        }
        return 0;
    }

    public static void seekTo(int sec) {
        if (player != null) {
            player.seekTo(sec);
        }
    }

    private void callEvent(String name) {
        Intent intent = new Intent(SERVICE_EVENT);
        intent.putExtra("name", name);
        sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public boolean onUnbind(Intent intent) { return false; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();

        assert action != null;
        switch (action) {
            case ACTION_PLAY:
                index = intent.getIntExtra("index", 0);
                play();
                callEvent("play");
                break;
            case ACTION_TOGGLE:
                toggle();
                break;
            case ACTION_SELECT:
                callEvent("select");
                break;
            case ACTION_NEXT:
                index = index == songs.size()-1 ? 0 : index+1;
                play();
                callEvent("next");
                break;
            case ACTION_PREV:
                index = index == 0 ? songs.size()-1 : index-1;
                play();
                callEvent("prev");
                break;
            case ACTION_STOP:
                player.stop();
                player.release();
                prepared = false;
                metadata = new HashMap<String, String>();
                customOptions = new ArrayList<Map>();
                index = 0;
                callEvent("stop");
                stopSelf();
                break;
        }

        return START_STICKY;
    }

    private void play() {
        play = true;
        String source = getSong().get("source");
        player.stop();
        player.seekTo(0);
        Uri url = Uri.parse(source);
        ExtractorMediaSource mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(url);

        player.prepare(mediaSource);
        player.setPlayWhenReady(true);
    }

    private void toggle() {
        play = !play;
        player.setPlayWhenReady(play);
        callEvent(play ? "play" : "pause");
    }

}

