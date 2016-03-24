package com.github.sonerik.rxexoplayer;

import android.net.Uri;
import android.support.annotation.NonNull;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.TrackRenderer;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;

import static com.google.android.exoplayer.ExoPlayer.Factory;
import static com.google.android.exoplayer.ExoPlayer.Listener;
import static com.google.android.exoplayer.ExoPlayer.STATE_BUFFERING;
import static com.google.android.exoplayer.ExoPlayer.STATE_ENDED;
import static com.google.android.exoplayer.ExoPlayer.STATE_IDLE;
import static com.google.android.exoplayer.ExoPlayer.STATE_PREPARING;
import static com.google.android.exoplayer.ExoPlayer.STATE_READY;

public abstract class RxExoPlayer {

    protected ExoPlayer innerPlayer;
    protected TrackRenderer currentRenderer;

    protected SerializedSubject<PlayerEvent, PlayerEvent> playerSubject = PublishSubject.<PlayerEvent>create().toSerialized();
    protected SerializedSubject<ExoPlaybackException, ExoPlaybackException> errorSubject = PublishSubject.<ExoPlaybackException>create().toSerialized();

    protected int lastState = STATE_IDLE;

    public enum PlayerEvent {
        READY, PREPARING, BUFFERING, STARTED, PAUSED, ENDED, IDLE
    }

    public Observable<PlayerEvent> events() {
        return playerSubject;
    }

    public Observable<PlayerEvent> event(final PlayerEvent e) {
        return playerSubject.filter(new Func1<PlayerEvent, Boolean>() {
            @Override
            public Boolean call(PlayerEvent event) {
                return event == e;
            }
        });
    }

    public Observable<ExoPlaybackException> errors() {
        return errorSubject;
    }

    private Listener listener = new Listener() {
        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            switch (playbackState) {
                case STATE_ENDED:
                    playerSubject.onNext(PlayerEvent.ENDED);
                    break;
                case STATE_IDLE:
                    playerSubject.onNext(PlayerEvent.IDLE);
                    break;
                case STATE_PREPARING:
                    playerSubject.onNext(PlayerEvent.PREPARING);
                    break;
                case STATE_BUFFERING:
                    playerSubject.onNext(PlayerEvent.BUFFERING);
                    break;
                case STATE_READY:
                    playerSubject.onNext(PlayerEvent.READY);
                    if (playWhenReady) {
                        playerSubject.onNext(PlayerEvent.STARTED);
                    }
                    break;
            }
            lastState = playbackState;
        }

        @Override
        public void onPlayWhenReadyCommitted() {
            if (!innerPlayer.getPlayWhenReady())
                playerSubject.onNext(PlayerEvent.PAUSED);
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            playerSubject.onError(error);
            errorSubject.onNext(error);
        }
    };

    public RxExoPlayer() {
        innerPlayer = Factory.newInstance(1);
//        innerPlayer = Factory.newInstance 1, 30 * 1000, 60 * 1000
        innerPlayer.addListener(listener);
        innerPlayer.setPlayWhenReady(false);
    }

    // region Player state
    public boolean isPaused() { return !innerPlayer.getPlayWhenReady(); }

    public boolean isPlaying() { return isReady() && !isPaused(); }

    public boolean isReady() { return innerPlayer.getPlaybackState() == STATE_READY; }

    public long getCurrentPosition() { return innerPlayer.getCurrentPosition(); }
    // endregion

    protected abstract TrackRenderer getRenderer(Uri uri);

    public Observable<PlayerEvent> start() {
        return Observable.defer(new Func0<Observable<PlayerEvent>>() {
            @Override
            public Observable<PlayerEvent> call() {
                if (innerPlayer.getPlayWhenReady()) {
                    return Observable.just(PlayerEvent.STARTED);
                } else {
                    return event(PlayerEvent.STARTED)
                            .take(1)
                            .doOnSubscribe(new Action0() {
                                @Override
                                public void call() {
                                    innerPlayer.setPlayWhenReady(true);
                                }
                            });
                }
            }
        });
    }

    public Observable<PlayerEvent> restart() {
        return seekTo(0)
                .concatMap(new Func1<PlayerEvent, Observable<PlayerEvent>>() {
                    @Override
                    public Observable<PlayerEvent> call(PlayerEvent event) {
                        return start();
                    }
                })
                .take(1);
    }

    public Observable<PlayerEvent> pause() {
        return Observable.defer(new Func0<Observable<PlayerEvent>>() {
            @Override
            public Observable<PlayerEvent> call() {
                if (!innerPlayer.getPlayWhenReady()) {
                    return Observable.just(PlayerEvent.PAUSED);
                } else {
                    return event(PlayerEvent.PAUSED)
                            .take(1)
                            .doOnSubscribe(new Action0() {
                                @Override
                                public void call() {
                                    innerPlayer.setPlayWhenReady(false);
                                }
                            });
                }
            }
        });
    }

    public Observable<PlayerEvent> setPaused(boolean flag) {
        return flag ? pause() : start();
    }

    public Observable<PlayerEvent> togglePause() { return setPaused(!isPaused()); }

    /**
     * Stop playback
     * @return true if playback stopped successfully and false means that error has occurred during playback stopping.
     */
    public Observable<PlayerEvent> stop() {
        return Observable.defer(new Func0<Observable<PlayerEvent>>() {
            @Override
            public Observable<PlayerEvent> call() {
                if (innerPlayer.getPlaybackState() == STATE_IDLE) {
                    return Observable.just(PlayerEvent.IDLE);
                } else {
                    return event(PlayerEvent.IDLE)
                            .take(1)
                            .doOnSubscribe(new Action0() {
                                @Override
                                public void call() {
                                    innerPlayer.stop();
                                }
                            });
                }
            }
        });
    }

    public Observable<PlayerEvent> prepare(@NonNull final Uri uri) {
        return event(PlayerEvent.PREPARING)
                .concatMap(new Func1<PlayerEvent, Observable<PlayerEvent>>() {
                    @Override
                    public Observable<PlayerEvent> call(PlayerEvent event) {
                        return event(PlayerEvent.READY);
                    }
                })
                .take(1)
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        currentRenderer = getRenderer(uri);
                        innerPlayer.prepare(currentRenderer);
                    }
                });
    }

    public Observable<PlayerEvent> seekTo(final long msec) {
        return Observable.defer(new Func0<Observable<PlayerEvent>>() {
            @Override
            public Observable<PlayerEvent> call() {
                if (lastState == STATE_IDLE) {
                    return Observable.just(PlayerEvent.IDLE);
                } else if (lastState == STATE_PREPARING) {
                    return Observable.just(PlayerEvent.PREPARING);
                } else if (innerPlayer.getCurrentPosition() == msec) {
                    return Observable.just(PlayerEvent.READY);
                } else {
                    return event(PlayerEvent.BUFFERING)
                            .concatMap(new Func1<PlayerEvent, Observable<PlayerEvent>>() {
                                @Override
                                public Observable<PlayerEvent> call(PlayerEvent event) {
                                    return event(PlayerEvent.READY);
                                }
                            })
                            .take(1)
                            .doOnSubscribe(new Action0() {
                                @Override
                                public void call() {
                                    innerPlayer.seekTo(msec);
                                }
                            });
                }
            }
        });
    }

    public Observable<PlayerEvent> reset() {
        return pause()
                .concatMap(new Func1<PlayerEvent, Observable<PlayerEvent>>() {
                    @Override
                    public Observable<PlayerEvent> call(PlayerEvent event) {
                        return seekTo(0);
                    }
                })
                .concatMap(new Func1<PlayerEvent, Observable<PlayerEvent>>() {
                    @Override
                    public Observable<PlayerEvent> call(PlayerEvent event) {
                        return stop();
                    }
                })
                .take(1);
    }
}