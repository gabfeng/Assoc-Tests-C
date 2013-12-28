package com.example.insclient;

import java.util.HashMap;
import java.util.Locale;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Handler;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;

public class Sounds extends Thread implements OnCompletionListener, OnInitListener{

	private MediaPlayer mediaPlayer;
	private Context context;
	private boolean mcompleted, tcompleted;
	private boolean inst;
	private Vibrator vibrator;
	private TextToSpeech tts;
	private Handler handler;
	
	/**
	 * @param _context
	 * @param _handler
	 * 
	 * Constructor, initialises vibrator service and text to speech, and stores information from parent class.
	 */
	public Sounds(Context _context, Handler _handler) {
		mediaPlayer = new MediaPlayer();
		context = _context;
		handler = _handler;
		vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
		tts = new TextToSpeech(context, this);
	}

	/**
	 * @see android.media.MediaPlayer.OnCompletionListener#onCompletion(android.media.MediaPlayer)
	 * 
	 * When the mediaPlayer finishes playing the sound file set a boolean to true.
	 */
	@Override
	public void onCompletion(MediaPlayer arg0) {
		// TODO Auto-generated method stub
		mcompleted = true;
	}
	
	/**
	 * Method call to stop vibration, text to speech, and media player.
	 */
	public void release() {
		stopTTS();
		vibrator.cancel();
		mediaPlayer.release();
	}
	
	/**
	 * Method to play a "ding" sound.
	 */
	public void beep() {
		mediaPlayer=MediaPlayer.create(context, R.raw.ding);
		mediaPlayer.start();
	}
	
	/**
	 * Method to play an emergency stop alert sound along with vibrations.
	 */
	public void estop() {
		tts.stop();
		mcompleted = false;
		mediaPlayer = MediaPlayer.create(context, R.raw.alert);
		mediaPlayer.start();
		int dot = 100;
		int shortgap = 50;
		int longgap = 300;
		long[] pattern = {0, dot, shortgap, dot, shortgap, dot, longgap};
		vibrator.vibrate(pattern,0);
	}

	/**
	 * @see android.speech.tts.TextToSpeech.OnInitListener#onInit(int)
	 * 
	 * Initialises TTS and Progress listener.
	 */
	@Override
	public void onInit(int status) {
		if(status == TextToSpeech.SUCCESS) {
			int result = tts.setLanguage(Locale.ENGLISH);
			if(result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
				handler.obtainMessage(3, ("TTS: Language not supported!")).sendToTarget();
			} else {
				int err = tts.setOnUtteranceProgressListener(/**
				 * @author Gabriel
				 *
				 * Progress listener for TTS. Send a message to parent class with ID 3 of TTS Status
				 */
				new UtteranceProgressListener() {
					@Override
					public void onDone(String utteranceID) {
						tcompleted=true;
						if(inst) {
							mediaPlayer = MediaPlayer.create(context, R.raw.go);
							mediaPlayer.start();
						}
						handler.obtainMessage(3, ("OK")).sendToTarget();
					}
					@Override
					public void onError(String utteranceID) {
						handler.obtainMessage(3, ("TTS: Error")).sendToTarget();
					}
					@Override
					public void onStart(String utteranceID) {
						tcompleted = false;
						handler.obtainMessage(3, ("TTS: Speaking")).sendToTarget();
					}
				});
				if(err == TextToSpeech.ERROR)
					handler.obtainMessage(3, ("Utterance Listener failed")).sendToTarget();
				else if(err == TextToSpeech.SUCCESS)
					handler.obtainMessage(3, ("Initialised")).sendToTarget();
			}
		} else {
			handler.obtainMessage(3, ("TTS: Initialisation failed!")).sendToTarget();
		}
		
	}
	
	/**
	 * @param strToPlay
	 * @param queue
	 * @param _inst
	 * @return result of TTS
	 * 
	 * Plays text based on string provided.
	 */
	public boolean playText(String strToPlay, int queue, boolean _inst) {
		inst = _inst;
		HashMap<String, String> hashAudio = new HashMap<String, String>();
        hashAudio.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "Script");
		if(tts.speak(strToPlay, queue, hashAudio)==TextToSpeech.SUCCESS) {
			return true;
		}
		else
			return false;
	}
	
	public void stopTTS() {
		tts.stop();
	}
	
	public void shutdownTTS() {
		tts.shutdown();
	}
	
	public boolean isTTSnull(){
		return (tts == null);
	}
	
}
