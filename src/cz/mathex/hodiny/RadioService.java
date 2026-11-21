package cz.mathex.hodiny;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.IBinder;
import android.os.PowerManager;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Přehrávání internetového rádia. Vlastní MediaSession → stanice se ukáže v kartě hudby i ve standby
 * a jde ovládat stejně jako Spotify. Při budíku (dočasná ztráta audio focusu) se pozastaví a pak pokračuje.
 */
public class RadioService extends Service {
    static final String PLAY = "cz.mathex.hodiny.radio.PLAY";
    static final String STOP = "cz.mathex.hodiny.radio.STOP";

    static String nowName, nowUrl; // pro zvýraznění v seznamu

    private MediaPlayer player;
    private MediaSession session;
    private AudioManager audio;
    private boolean pausedByFocus;
    private String name, url;

    static void play(Context c, String name, String url) {
        c.startService(new Intent(c, RadioService.class).setAction(PLAY).putExtra("name", name).putExtra("url", url));
    }

    static void stop(Context c) {
        c.startService(new Intent(c, RadioService.class).setAction(STOP));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        session = new MediaSession(this, "Hodiny rádio");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(PendingIntent.getActivity(this, 5, new Intent(this, RadioActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT));
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onStop() { stopAll(); }
            @Override public void onSkipToNext() { skip(1); }
            @Override public void onSkipToPrevious() { skip(-1); }
        });
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        if (i != null && PLAY.equals(i.getAction())) {
            start(i.getStringExtra("name"), i.getStringExtra("url"));
        } else if (i != null && STOP.equals(i.getAction())) {
            stopAll();
        }
        return START_NOT_STICKY;
    }

    private final AudioManager.OnAudioFocusChangeListener focus = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int change) {
            if (change == AudioManager.AUDIOFOCUS_LOSS) {
                pause();
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                if (player != null && player.isPlaying()) {
                    pausedByFocus = true;
                    player.pause();
                    state(PlaybackState.STATE_PAUSED);
                }
            } else if (change == AudioManager.AUDIOFOCUS_GAIN && pausedByFocus) {
                pausedByFocus = false;
                resume();
            }
        }
    };

    private void start(String n, String u) {
        if (u == null) return;
        release();
        name = n;
        url = u;
        nowName = n;
        nowUrl = u;
        audio.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        session.setActive(true);
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, n)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "📻 rádio")
                .build());
        state(PlaybackState.STATE_BUFFERING);
        foreground();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(u);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                    state(PlaybackState.STATE_PLAYING);
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Toast.makeText(RadioService.this, "Stanice „" + name + "“ nejde přehrát", Toast.LENGTH_LONG).show();
                    stopAll();
                    return true;
                }
            });
            player.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, "Stanice nejde přehrát", Toast.LENGTH_LONG).show();
            stopAll();
        }
    }

    private void resume() {
        if (player == null) {
            if (url != null) start(name, url);
            return;
        }
        audio.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        // živý stream po pauze navázat znovu (jinak hraje zpožděně)
        start(name, url);
    }

    private void pause() {
        pausedByFocus = false;
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        state(PlaybackState.STATE_PAUSED);
    }

    /** Další / předchozí stanice z oblíbených. */
    private void skip(int dir) {
        try {
            JSONArray fav = new JSONArray(getSharedPreferences("radio", MODE_PRIVATE).getString("fav", "[]"));
            if (fav.length() == 0) return;
            int idx = -1;
            for (int i = 0; i < fav.length(); i++) {
                if (fav.getJSONObject(i).optString("url").equals(url)) idx = i;
            }
            int next = ((idx < 0 ? 0 : idx + dir) % fav.length() + fav.length()) % fav.length();
            JSONObject s = fav.getJSONObject(next);
            start(s.getString("name"), s.getString("url"));
        } catch (Exception ignored) {
        }
    }

    private void state(int st) {
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions)
                .setState(st, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build());
    }

    @SuppressWarnings("deprecation")
    private void foreground() {
        Notification n = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("📻 " + name)
                .setContentText("Hodiny – rádio")
                .setContentIntent(PendingIntent.getActivity(this, 6, new Intent(this, RadioActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT))
                .setOngoing(true)
                .build();
        startForeground(42, n);
    }

    private void release() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
    }

    private void stopAll() {
        release();
        nowName = null;
        nowUrl = null;
        audio.abandonAudioFocus(focus);
        state(PlaybackState.STATE_STOPPED);
        session.setActive(false);
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        release();
        session.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }
}
