package com.example.insclient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;

public class Tracker extends Thread implements SensorEventListener{

	public static final int DEGREE_ERROR = 10;
	public static final int VIB = 30;
	public static final int GAP = 30;
	
	MainActivity parent;
	private Context context;
	private SensorManager mSensorManager;
    private Sensor mAccelerometer;
	private Sensor mCompass;
	private Sensor mLAccel;
	private Vibrator vibrator;
	private Handler handler;
    private float[] valuesAccelerometer;
    private float[] valuesLAccel;
    private float[] valuesMagneticField;
    private float[] matrixR;
    private float[] matrixI;
    private float[] matrixValues;
    private LinkedList<float[]> logData;
    private LinkedList<Long> timeStamps;
    private LinkedList<String[]> msgs;
    private String[] text;
    private Date ts;
    private boolean logging;
    private boolean turnL, turnR;
    private boolean vibed;
	private int degrees;
	private long initO;
	private long[] pattern = {0,VIB,GAP,VIB,GAP,VIB,GAP};
	private String name;
	private Timer loggerT;
    private FileWriter writer;
    
    public Tracker(Context _context, Handler _handle, MainActivity _parent) {
    	context = _context;
    	handler = _handle;
    	parent = _parent;
    	
    	
		vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
		vibrator.vibrate(100); //Confirmation start of tracker
    	// Get an instance of the SensorManager
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mLAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    	mCompass = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    	mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    	valuesAccelerometer = new float[3];
    	valuesLAccel = new float[3];
    	valuesMagneticField = new float[3];
    	matrixR = new float[9];
    	matrixI = new float[9];
    	matrixValues = new float[3];
    	logData = new LinkedList<float[]>();
		timeStamps = new LinkedList<Long>();
		msgs = new LinkedList<String[]>();
		ts = new Timestamp(System.currentTimeMillis());
		name = "No name";
    }
    
    public void resumeTracker() {
    	if(mSensorManager != null) {
    		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
    		mSensorManager.registerListener(this, mCompass, SensorManager.SENSOR_DELAY_FASTEST);
    		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
    		mSensorManager.registerListener(this, mLAccel, SensorManager.SENSOR_DELAY_FASTEST);
    		parent.sendTrackerInfo(2);
    	}
    }
    
    public void stopTracker() {
    	if(mSensorManager != null) {
    		mSensorManager.unregisterListener(this,mAccelerometer);
    		mSensorManager.unregisterListener(this,mCompass);  
    		mSensorManager.unregisterListener(this,mAccelerometer);  
    		mSensorManager.unregisterListener(this,mLAccel);
    		parent.sendTrackerInfo(4);
    	}
    	if(logging) {
    		stopLog();
    	}
    }
    
    public void startLog() {
    	logging = true;
    	logData.clear(); msgs.clear();
    	loggerT = new Timer();
    	loggerT.scheduleAtFixedRate(new TimerTask() {
    		public void run() {
    	        //Called every 10 milliseconds(the period parameter)
    			float[] values = new float[6];
    			for(int i=0;i<6;i++) {
    				if(i<3)
    					values[i]=matrixValues[i];
    				else
    					values[i]=valuesLAccel[i-3];
    			}			
    			timeStamps.add(System.currentTimeMillis());
    			logData.add(values);
    	    }
    	}, 0, 10);
    	handler.obtainMessage(3,"Logger started...").sendToTarget();
    }
    
    public void stopLog() {
    	log();
    	logging = false;
    	loggerT.cancel();
    	loggerT.purge();
    }
    
    public void name(String _name) {
    	name = String.copyValueOf(_name.toCharArray());
    	handler.obtainMessage(0, "Name set to " + name).sendToTarget();
    }
    
    public void saveMsg(String msg) {
    	Date d = new Date(System.currentTimeMillis());
    	SimpleDateFormat sdf=new SimpleDateFormat("hh:mm:ss.SS", Locale.UK);
    	text = new String[2];
    	text[0] = msg;
    	text[1] = sdf.format(d);
    	msgs.add(text);
    }
    
    public void setDegrees(int deg) {
		degrees = deg;
	}
    
    public void setLR (int d) {
    	if(d==1)
    		turnL=true;
    	else if(d==2)
    		turnR=true;
    }
	
    public long setFace() {
    	initO = ((Long) Math.round(Math.toDegrees(matrixValues[0]))).intValue();
    	if(initO < 0) {
	  		initO = 360+initO;
	  	}
    	return initO;
    }
    
    public void checkTurnR(long ori) {
    	ori+=(360-initO);
    	if(ori < 0) {
	  		ori += 360;
	  	} else if (ori > 360){
	  		ori -= 360;
	  	}
    	if(ori >= degrees-DEGREE_ERROR && ori <= degrees+DEGREE_ERROR && !vibed) {
    		vibrator.vibrate(pattern,-1);
    		vibed = true;
    	} else if(ori < degrees-DEGREE_ERROR || ori > degrees+DEGREE_ERROR){
    		vibed = false;
    		vibrator.cancel();
    	}
    }
    
    public void checkTurnL(long ori) {
    	long tmp = initO;
    	tmp+=(360-ori);
    	if(tmp < 0) {
	  		tmp += 360;
	  	} else if (tmp > 360){
	  		tmp -= 360;
	  	}
    	if(tmp >= degrees-DEGREE_ERROR && tmp <= degrees+DEGREE_ERROR && !vibed) {
    		vibrator.vibrate(pattern,-1);
    		vibed = true;
    	} else if(tmp < degrees-DEGREE_ERROR || tmp > degrees+DEGREE_ERROR){
    		vibed = false;
    		vibrator.cancel();
    	}
    }
    
    public void turnFin() {
    	turnL = false; turnR = false;
    }
    
	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		if (arg0 == mAccelerometer && arg1 == SensorManager.SENSOR_STATUS_UNRELIABLE) {
			parent.sendTrackerInfo(3);
		} else if (arg0 == mAccelerometer && arg1 != SensorManager.SENSOR_STATUS_UNRELIABLE) {
			parent.sendTrackerInfo(2);
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		switch(event.sensor.getType()){
		case Sensor.TYPE_ACCELEROMETER:
			for(int i =0; i < 3; i++){
				valuesAccelerometer[i] = event.values[i];
			}

			break;
		case Sensor.TYPE_MAGNETIC_FIELD:
			for(int i =0; i < 3; i++){
				valuesMagneticField[i] = event.values[i];
			}
			break;
		case Sensor.TYPE_LINEAR_ACCELERATION:
			for(int i =0; i < 3; i++){
				valuesLAccel[i] = event.values[i];
			}
			break;
		}
		boolean success = SensorManager.getRotationMatrix(
				matrixR,
				matrixI,
				valuesAccelerometer,
				valuesMagneticField);
		if(success)
			SensorManager.getOrientation(matrixR, matrixValues);
		handler.obtainMessage(5, String.valueOf(((Long) Math.round(Math.toDegrees(matrixValues[0]))).intValue()) ).sendToTarget();
		
		if(turnL) {
			checkTurnL(((Long) Math.round(Math.toDegrees(matrixValues[0]))).intValue());			
		}else if (turnR) {
			checkTurnR(((Long) Math.round(Math.toDegrees(matrixValues[0]))).intValue());
		}
		
		return; //else?
	}

	public void log() {
		String appPath = Environment.getExternalStorageDirectory().toString();
		File appDir = new File(appPath + "/" + "logs");
		appDir.mkdirs();
		File appFile = new File(appDir.toString() + "/" + name +".csv");
		int x = 1;
		SimpleDateFormat sdf=new SimpleDateFormat("hh:mm:ss.SS", Locale.UK);
		while(appFile.exists()) {
			appFile = new File(appDir.toString() + "/" + name +"_" + Integer.toString(x) + ".csv");
			x++;
		}
		Date date = new Date(System.currentTimeMillis());
		float[] tmp = new float[6];
		String timeS = new String();
		ts= new Date(System.currentTimeMillis());
		try{
			writer = new FileWriter(appFile);
			writer.write("co-ords based on http://developer.android.com/reference/android/hardware/SensorEvent.html\n");
			writer.write("Date,"+date.toString()+"\n");
			writer.write("Name,"+name+"\n");
			writer.write("Time,Message\n");
			writeMsgs();
			writer.write("timestamp,azimuth,pitch,roll,(linear acceleration) x,y,z\n");
			while(!logData.isEmpty()) {
				tmp = logData.remove();
				ts.setTime(timeStamps.remove());
				timeS = sdf.format(ts);
				writeData(timeS,tmp[0],tmp[1],tmp[2],tmp[3],tmp[4],tmp[5]);
			}
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		handler.obtainMessage(3, "Finished Logging!").sendToTarget();
	}

	public void writeData(String ts, float a, float p, float r, float x, float y, float z) throws IOException {
		String line = String.format("%s,%f,%f,%f,%f,%f,%f\n", ts,a,p,r,x,y,z);
		writer.write(line);
	}

	public void writeMsgs() throws IOException{
		int i = 0;
		while(!msgs.isEmpty()) {
			String[] tmp =msgs.remove();
			writer.write(tmp[1]+","+tmp[0]+"\n");
			i++;
		}
	}
}
