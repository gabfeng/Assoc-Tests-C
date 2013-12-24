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
	
	public Responder(Context _context, Sounds _sounds, Tracker _tracker, MainActivity _parent) {
		sounds = _sounds;
		tracker = _tracker;
		parent = _parent;
		context = _context;
		vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
	}
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
	
	public void expectTouch() {
		waitingForTouch = true;
	}

}
