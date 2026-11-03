package com.omelette.aufromage

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.format.DateUtils

class MainActivity : Activity() {
    private val bg = Color.rgb(16,25,39); private val card = Color.rgb(28,42,59); private val mint = Color.rgb(157,229,215); private val muted = Color.rgb(159,176,192); private val white = Color.rgb(245,248,250)
    private lateinit var root: LinearLayout; private lateinit var record: Button; private lateinit var play: Button; private lateinit var preview: Button; private lateinit var status: TextView; private lateinit var slider: SeekBar; private lateinit var percent: TextView; private lateinit var normalize: CheckBox
    private var recording = false; private var recorder: AudioRecord? = null; private var writer: Thread? = null; private var stopWrite = false
    private val handler = Handler(Looper.getMainLooper())
    private val loop = object: Runnable { override fun run() { refresh(); handler.postDelayed(this, 1000) } }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); window.statusBarColor=bg; window.navigationBarColor=bg; buildUi(); handler.post(loop) }
    override fun onDestroy() { handler.removeCallbacks(loop); super.onDestroy() }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport=true; clipToPadding=true }
        root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(24),dp(18),dp(24),dp(32)) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        scroll.addView(root); setContentView(scroll)
        val top = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val icon = ImageView(this).apply { setImageResource(R.drawable.omelette_icon) }; top.addView(icon, LinearLayout.LayoutParams(dp(54),dp(54)))
        val heading = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(13),0,0,0) }
        heading.addView(text("omelette au fromage",21,white,true)); heading.addView(text("Votre rituel du sommeil",13,muted,false)); top.addView(heading); root.addView(top)
        root.addView(space(24))
        val hero = panel(); hero.setPadding(dp(21),dp(22),dp(21),dp(22))
        hero.addView(text("Une voix qui vous accompagne",20,white,true)); hero.addView(space(8)); hero.addView(text("Enregistrez votre message. Il démarrera doucement, une heure après le lancement, avec votre musique en fond.",14,muted,false))
        val badge = text("  ☾   MODE NUIT · LECTURE ÉCRAN ÉTEINT  ",11,mint,true).apply { setPadding(dp(10),dp(9),dp(10),dp(9)); background=shape(Color.rgb(35,63,69),22) }
        hero.addView(space(16)); hero.addView(badge); root.addView(hero)
        root.addView(space(18))
        val recPanel=panel(); recPanel.setPadding(dp(20),dp(18),dp(20),dp(20))
        recPanel.addView(text("1  ·  VOTRE MESSAGE",12,mint,true)); recPanel.addView(space(7)); recPanel.addView(text("Enregistrez quelques mots, une intention ou une affirmation.",14,muted,false)); recPanel.addView(space(16))
        record=Button(this).apply { text="●   REC  ·  ENREGISTRER"; isAllCaps=false; textSize=14f; setTextColor(bg); background=shape(mint,16); setOnClickListener { recordClick() } }; recPanel.addView(record, LinearLayout.LayoutParams(-1,dp(54)))
        status=text("Aucun message enregistré",12,muted,false).apply { gravity=Gravity.CENTER; setPadding(0,dp(10),0,0) }; recPanel.addView(status); root.addView(recPanel)
        root.addView(space(14))
        val options=panel(); options.setPadding(dp(20),dp(18),dp(20),dp(20)); options.addView(text("2  ·  VOTRE AMBIANCE",12,mint,true)); options.addView(space(8)); options.addView(text("Volume de la musique",15,white,true))
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        slider=SeekBar(this).apply { max=100; progress=getPreferences(0).getInt("musicVolume",25); progressTintList=android.content.res.ColorStateList.valueOf(mint); thumbTintList=android.content.res.ColorStateList.valueOf(mint) }
        percent=text("25 %",14,mint,true).apply { gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL }; row.addView(slider,LinearLayout.LayoutParams(0,dp(42),1f)); row.addView(percent,LinearLayout.LayoutParams(dp(56),dp(42))); options.addView(row)
        slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s:SeekBar?,v:Int,u:Boolean) { percent.text="$v %"; getPreferences(0).edit().putInt("musicVolume",v).apply(); sendService(AudioLoopService.ACTION_VOLUME,v) }; override fun onStartTrackingTouch(s:SeekBar?){}; override fun onStopTrackingTouch(s:SeekBar?){} })
        normalize=CheckBox(this).apply { text="Normaliser le volume du message"; textSize=14f; setTextColor(white); buttonTintList=android.content.res.ColorStateList.valueOf(mint); isChecked=getPreferences(0).getBoolean("normalize",false); setOnCheckedChangeListener { _,checked -> getPreferences(0).edit().putBoolean("normalize",checked).apply() } }; options.addView(normalize)
        options.addView(text("Ajustez la musique pour garder votre voix bien distincte.",12,muted,false)); root.addView(options)
        root.addView(space(14))
        val actions=panel(); actions.setPadding(dp(20),dp(18),dp(20),dp(20)); actions.addView(text("3  ·  LANCER LA SÉANCE",12,mint,true)); actions.addView(space(8)); actions.addView(text("PLAY attend une heure avant de commencer. Aperçu lance les boucles immédiatement.",14,muted,false)); actions.addView(space(16))
        play=Button(this).apply { text="▶   PLAY  ·  DÉMARRER DANS 1 H"; isAllCaps=false; textSize=14f; setTextColor(bg); background=shape(mint,16); setOnClickListener { startSession(false) } }; actions.addView(play,LinearLayout.LayoutParams(-1,dp(54)))
        actions.addView(space(10)); preview=Button(this).apply { text="Aperçu immédiat"; isAllCaps=false; textSize=14f; setTextColor(white); background=shape(Color.rgb(54,69,86),16); setOnClickListener { startSession(true) } }; actions.addView(preview,LinearLayout.LayoutParams(-1,dp(50)))
        val stop=Button(this).apply { text="■   Arrêter la lecture"; isAllCaps=false; textSize=14f; setTextColor(muted); background=shape(Color.TRANSPARENT,16); setOnClickListener { sendService(AudioLoopService.ACTION_STOP); refresh() } }; actions.addView(stop,LinearLayout.LayoutParams(-1,dp(48))); root.addView(actions)
        root.addView(space(14)); root.addView(text("La voix se répète avec 1 seconde de pause. La musique tourne sans interruption. Gardez votre téléphone branché pour une nuit complète.",12,muted,false))
        refresh()
    }
    private fun recordClick() {
        if (recording) { stopRecording(); return }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),7); return}
        val rate=44100; val size=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(8192)
        try { recorder=AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size); recorder!!.startRecording(); stopWrite=false; recording=true; record.text="■   ARRÊTER L'ENREGISTREMENT"; status.text="Enregistrement en cours…"; writer=Thread { capture(size,rate) }.also { it.start() } } catch(e:Exception){ Toast.makeText(this,"Impossible de démarrer le micro",Toast.LENGTH_LONG).show() }
    }
    private fun capture(size:Int,rate:Int) {
        val samples=java.io.ByteArrayOutputStream(); val buf=ShortArray(size/2)
        while(!stopWrite){ val n=recorder?.read(buf,0,buf.size)?:-1; if(n>0){ val bytes=ByteArray(n*2); for(i in 0 until n){val s=buf[i].toInt(); bytes[2*i]=s.toByte(); bytes[2*i+1]=(s shr 8).toByte()}; samples.write(bytes) } }
        val pcm=samples.toByteArray(); if(pcm.isEmpty())return
        if(normalize.isChecked){ var peak=0; var i=0; while(i+1<pcm.size){ val v= kotlin.math.abs(((pcm[i+1].toInt() shl 8) or (pcm[i].toInt() and 255)).toShort().toInt()); if(v>peak)peak=v; i+=2 }; if(peak>0){ val gain=32767.0/peak; i=0; while(i+1<pcm.size){ val v=((pcm[i+1].toInt() shl 8) or (pcm[i].toInt() and 255)).toShort().toInt(); val x=(v*gain).toInt().coerceIn(-32768,32767); pcm[i]=x.toByte(); pcm[i+1]=(x shr 8).toByte(); i+=2 } } }
        val f=java.io.File(filesDir,"message.wav"); java.io.DataOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(f))).use { out -> out.writeBytes("RIFF"); writeLE(out,pcm.size+36); out.writeBytes("WAVEfmt "); writeLE(out,16); writeLE(out,1,2); writeLE(out,1,2); writeLE(out,rate); writeLE(out,rate*2); writeLE(out,2,2); writeLE(out,16,2); out.writeBytes("data"); writeLE(out,pcm.size); out.write(pcm) }
        runOnUiThread { status.text="Message prêt · ${DateUtils.formatElapsedTime(pcm.size/2/rate.toLong())}"; refresh() }
    }
    private fun writeLE(o:java.io.DataOutputStream,v:Int,n:Int=4){ for(i in 0 until n)o.writeByte((v ushr (8*i)) and 255) }
    private fun stopRecording(){ stopWrite=true; recording=false; try{recorder?.stop(); recorder?.release()}catch(_:Exception){}; recorder=null; record.text="●   REC  ·  ENREGISTRER"; status.text="Finalisation du message…" }
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){ super.onRequestPermissionsResult(r,p,g); if(r==7&&g.firstOrNull()==PackageManager.PERMISSION_GRANTED)recordClick() }
    private fun startSession(previewNow:Boolean){ if(!java.io.File(filesDir,"message.wav").exists()){Toast.makeText(this,"Enregistrez d'abord votre message",Toast.LENGTH_SHORT).show();return}; val i=Intent(this,AudioLoopService::class.java).apply { action=AudioLoopService.ACTION_START; putExtra("preview",previewNow); putExtra("volume",slider.progress); putExtra("normalize",normalize.isChecked) }; if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i); refresh() }
    private fun sendService(action:String,v:Int=slider.progress){startService(Intent(this,AudioLoopService::class.java).apply{this.action=action;putExtra("volume",v)})}
    private fun refresh(){ if(!::play.isInitialized)return; val prefs=getSharedPreferences("session",Context.MODE_PRIVATE); val state=prefs.getString("state","idle")?:"idle"; val end=prefs.getLong("end",0L); val has=java.io.File(filesDir,"message.wav").exists(); play.isEnabled=has; preview.isEnabled=has; if(has&& !recording && status.text.toString().startsWith("Aucun"))status.text="Message prêt · prêt pour votre séance"; if(state=="countdown"){val left=((end-System.currentTimeMillis()).coerceAtLeast(0))/1000; play.text="◷   DÉMARRAGE DANS ${DateUtils.formatElapsedTime(left)}"; preview.text="Aperçu immédiat"} else if(state=="playing"){play.text="♫   SÉANCE EN COURS"; preview.text="Aperçu immédiat"}else{play.text="▶   PLAY  ·  DÉMARRER DANS 1 H";preview.text="Aperçu immédiat"} }
    private fun text(s:String,size:Int,color:Int,bold:Boolean)=TextView(this).apply{text=s;textSize=size.toFloat();setTextColor(color);if(bold)typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); setLineSpacing(dp(3).toFloat(),1f)}
    private fun panel()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=shape(card,22)}
    private fun shape(color:Int,r:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(r).toFloat()}
    private fun space(n:Int)=View(this).apply{layoutParams=LinearLayout.LayoutParams(1,dp(n))}
    private fun dp(n:Int)= (n*resources.displayMetrics.density).toInt()
}
