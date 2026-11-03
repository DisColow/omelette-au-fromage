package com.omelette.aufromage

import android.app.*
import android.content.Intent
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.*

class AudioLoopService : Service() {
    companion object {
        const val ACTION_START="com.omelette.aufromage.START"
        const val ACTION_STOP="com.omelette.aufromage.STOP"
        const val ACTION_VOLUME="com.omelette.aufromage.VOLUME"
        const val ACTION_MEDIA_VOLUME="com.omelette.aufromage.MEDIA_VOLUME"
        private const val CHANNEL="sleep_audio"
        private const val MEDIA_VOLUME_CHECK_INTERVAL_MS=5000L
    }
    private val handler=Handler(Looper.getMainLooper()); private var voice:MediaPlayer?=null; private var music:MediaPlayer?=null; private var wake:PowerManager.WakeLock?=null; private var volume=0.25f; private var mediaVolumePercent=25; private var started=false; private var playing=false; private var delayed:Runnable?=null
    private val mediaVolumeCheck=object:Runnable { override fun run() { if(!started||!playing)return; enforceMediaVolume(); handler.postDelayed(this,MEDIA_VOLUME_CHECK_INTERVAL_MS) } }
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        when(intent?.action){ ACTION_STOP -> stopSession(); ACTION_VOLUME -> { volume=(intent.getIntExtra("volume",25)/100f); music?.setVolume(volume,volume) }; ACTION_MEDIA_VOLUME -> { mediaVolumePercent=intent.getIntExtra("volume",25).coerceIn(0,100); if(playing)enforceMediaVolume() }; ACTION_START -> startSession(intent.getBooleanExtra("preview",false),intent.getIntExtra("volume",25),intent.getIntExtra("mediaVolume",25),intent.getIntExtra("delayMinutes",60)) }
        return START_NOT_STICKY
    }
    private fun startSession(preview:Boolean,v:Int,mediaVolume:Int,delayMinutes:Int){ stopPlayers(); volume=v/100f; mediaVolumePercent=mediaVolume.coerceIn(0,100); started=true; val prefs=getSharedPreferences("session",MODE_PRIVATE); val delayMillis=delayMinutes.coerceAtLeast(1)*60*1000L; val end=if(preview)0L else System.currentTimeMillis()+delayMillis; prefs.edit().putString("state",if(preview)"playing" else "countdown").putLong("end",end).apply(); ensureChannel(); startForeground(41,notification(if(preview)"Aperçu en cours" else "Votre message démarre dans ${durationLabel(delayMinutes)}")); val pm=getSystemService(POWER_SERVICE) as PowerManager; wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"OmeletteAuFromage:nightAudio").apply{setReferenceCounted(false);acquire()}
        if(preview) startPlayback() else { delayed=Runnable { if(started){getSharedPreferences("session",MODE_PRIVATE).edit().putString("state","playing").apply();startForeground(41,notification("Votre séance est en cours"));startPlayback()} }; handler.postDelayed(delayed!!,delayMillis) }
    }
    private fun durationLabel(minutes:Int):String = when { minutes%60==0 -> "${minutes/60} heure${if(minutes==60)"" else "s"}"; minutes>60 -> "${minutes/60} heure ${minutes%60} minutes"; else -> "$minutes minutes" }
    private fun startPlayback(){
        enforceMediaVolume()
        try { music=MediaPlayer().apply { setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()); val fd=assets.openFd("calming_zen.mp3"); setDataSource(fd.fileDescriptor,fd.startOffset,fd.length); fd.close(); isLooping=true; setVolume(volume,volume); prepare(); start() } } catch(e:Exception){ stopSession(); return }
        startVoice()
        if(started){ playing=true; handler.postDelayed(mediaVolumeCheck,MEDIA_VOLUME_CHECK_INTERVAL_MS) }
    }
    private fun enforceMediaVolume(){ try { val audioManager=getSystemService(AUDIO_SERVICE) as AudioManager; val max=audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC); if(max>0){ val target=((max*mediaVolumePercent)+50)/100; if(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)!=target)audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,target,0) } } catch(e:SecurityException){ android.util.Log.w("AudioLoopService","Unable to adjust media volume",e) } }
    private fun startVoice(){ if(!started)return; try { val f=java.io.File(filesDir,"message.wav"); voice=MediaPlayer().apply { setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()); setDataSource(f.absolutePath); setVolume(1f,1f); setOnCompletionListener { if(started)handler.postDelayed({if(started)startVoice()},1000) }; prepare(); start() } }catch(e:Exception){ stopSession() } }
    private fun notification(line:String):Notification { val stop=PendingIntent.getService(this,2,Intent(this,AudioLoopService::class.java).setAction(ACTION_STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); val b=if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,CHANNEL) else Notification.Builder(this); return b.setSmallIcon(R.drawable.omelette_icon).setContentTitle("omelette au fromage").setContentText(line).setOngoing(true).setOnlyAlertOnce(true).addAction(0,"Arrêter",stop).build() }
    private fun ensureChannel(){if(Build.VERSION.SDK_INT>=26){val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager;nm.createNotificationChannel(NotificationChannel(CHANNEL,"Lecture nocturne",NotificationManager.IMPORTANCE_LOW).apply{description="Lecture audio en arrière-plan"})}}
    private fun stopPlayers(){ handler.removeCallbacksAndMessages(null); delayed=null; playing=false; try{voice?.setOnCompletionListener(null);voice?.stop();voice?.release()}catch(_:Exception){};voice=null; try{music?.stop();music?.release()}catch(_:Exception){};music=null }
    private fun stopSession(){ started=false;stopPlayers();getSharedPreferences("session",MODE_PRIVATE).edit().putString("state","idle").putLong("end",0).apply(); try{wake?.release()}catch(_:Exception){};wake=null;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf() }
    override fun onDestroy(){ if(started)stopSession();super.onDestroy() }
}
