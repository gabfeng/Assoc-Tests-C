package com.example.insclient;

import android.content.Context;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

public class Responder implements OnTouchListener{
	
	private MainActivity parent;
	private Sounds sounds;
	private Tracker tracker;
	private Context context;
	private boolean waitingForTouch;
	private Vibrator vibrator;
	
	/**
	 * @param _context
	 * @param _sounds
	 * @param _tracker
	 * @param _parent
	 * 
	 * Constructor, initialises vibrator service for haptic feedback, and stores information from parent class.
	 */
	public Responder(Context _context, Sounds _sounds, Tracker _tracker, MainActivity _parent) {
		sounds = _sounds;
		tracker = _tracker;
		parent = _parent;
		context = _context;
		vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
	}
	/**
	 * @see android.view.View.OnTouchListener#onTouch(android.view.View, android.view.MotionEvent)
	 * 
	 * Called when screen is touched. To stop sounds being played and let server application know client has completed a task.
	 */
	@Override
	public boolean onTouch(View arg0, MotionEvent arg1) {
		// TODO Auto-generated method stub
		if(waitingForTouch) {
			sounds.release();
			sounds.beep();
			tracker.turnFin();
			parent.setReady();
			vibrator.vibrate(20);
			waitingForTouch = false;
		}
		return false;
	}
	
	/**
	 * Sets a variable for onTouch method to do work.
	 */
	public void expectTouch() {
		waitingForTouch = true;
	}

}
