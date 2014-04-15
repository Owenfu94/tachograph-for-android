package com.scutchenhao.tachograph;

import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;

import java.io.File;
import java.util.Date;
import java.util.Locale;

import android.graphics.PixelFormat;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnInfoListener;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.GestureDetector.OnGestureListener;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
	public final static String PATH = Environment.getExternalStorageDirectory().getPath() + "/ScutTachograph/";
	public final static int TURN_LEFT = 1;
	public final static int TURN_RIGHT = 2;
	private Button start;// ��ʼ¼�ư�ť 
    private Button stop;// ֹͣ¼�ư�ť 
    private MediaRecorder mediaRecorder;// ¼����Ƶ���� 
    private SurfaceView surfaceView;// ��ʾ��Ƶ�Ŀؼ� 
    // ������ʾ��Ƶ��һ���ӿڣ��ҿ����û����У�Ҳ����˵��mediaRecorder¼����Ƶ���ø������濴 
    private boolean isRecording;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
        // ѡ��֧�ְ�͸��ģʽ,����surfaceView��activity��ʹ�á� 
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        setContentView(R.layout.activity_main);
        
        //����ʶ��
		mGestureDetector = new GestureDetector(this, mGestureListener, null);
        mGestureDetector.setIsLongpressEnabled(true);
        
        layoutInit();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	
	/*
	 * ����ʶ��
	 */
	private GestureDetector mGestureDetector;  
	private OnGestureListener mGestureListener = new OnGestureListener() {
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			if (velocityX < -1500)
				newActivity(MainActivity.TURN_LEFT);
			else if (velocityX > 1500)
				newActivity(MainActivity.TURN_RIGHT);
			return false;
		}

		@Override
		public void onLongPress(MotionEvent e) {
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
				float distanceY) {
			return false;
		}

		@Override
		public void onShowPress(MotionEvent e) {
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			return false;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			return false;
		}
	};
	
	//�������¼�����mGestureDetector���������޷�ʶ��
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		mGestureDetector.onTouchEvent(ev);
		return super.dispatchTouchEvent(ev);
	}

	protected Class<?> rightClass() {
		return GPSMapActivity.class;
	}

	protected Class<?> leftClass() {
		return GPSMapActivity.class;
	}

	protected Activity my() {
		return MainActivity.this;
	}
    
	private void newActivity(int dir) {
		Class<?> nextClass;
		
		if (dir == MainActivity.TURN_LEFT)
			nextClass = rightClass();
		else 
			nextClass = leftClass();
		
		if (nextClass == null)
			return;
		
		Intent intent = new Intent();
		intent.setClass(my(), nextClass);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);

		if (dir == MainActivity.TURN_LEFT)		//�����л����������ұ߽��룬����˳�
			overridePendingTransition(R.animator.in_from_right, R.animator.out_to_left);
		if (dir == MainActivity.TURN_RIGHT)	//�����л�����������߽߱��룬�ұ߱��˳�
			overridePendingTransition(R.animator.in_from_left, R.animator.out_to_right);		
		
		finish();
	}    
	
	
	private void layoutInit() { 
        start = (Button) this.findViewById(R.id.start);
        stop = (Button) this.findViewById(R.id.stop);
        start.setOnClickListener(new TestVideoListener());
        stop.setOnClickListener(new TestVideoListener());
        setRecordState(false);
        surfaceView = (SurfaceView) this.findViewById(R.id.surfaceview);

    } 
	
	@Override
	protected void onStop() {
		super.onStop();
		if(isRecording)
			stopRecording();
	}

	private void setRecordState(boolean state) {
		if(state) {
			start.setEnabled(false);
	        stop.setEnabled(true);
	        isRecording = true;
		} else {
			start.setEnabled(true);
	        stop.setEnabled(false);
	        isRecording = false;
		}
	}
	
	private void mediaRecorderInit() {
        mediaRecorder = new MediaRecorder();// ����mediaRecorder���� 
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        // ������Ƶ¼�Ƶķֱ��ʡ�����������ñ���͸�ʽ�ĺ��棬���򱨴� 
        mediaRecorder.setVideoSize(320, 240);
        mediaRecorder.setVideoFrameRate(5);
        mediaRecorder.setPreviewDisplay(surfaceView.getHolder().getSurface());
        // ���洢Ŀ¼����
        File file = new File(PATH);
        if(!file.isDirectory() || !file.exists()) {
        	if(!file.mkdir())
        		Toast.makeText(this, "Ŀ¼��ʼ��ʧ��", Toast.LENGTH_SHORT).show();
        }
        // ������Ƶ�ļ������·�� 
        String fileName = new java.text.SimpleDateFormat("yyyy-MM-dd-HH:mm:ss", Locale.US).format(new Date()) + ".3gp";
        mediaRecorder.setOutputFile(PATH  + fileName);
        
        mediaRecorder.setOnInfoListener(new OnInfoListener() {                 
        	@Override
        	public void onInfo(MediaRecorder mr, int what, int extra) {
        		// ��������ֹͣ¼��
        		mediaRecorder.stop();
        		mediaRecorder.release();
        		mediaRecorder = null;
        		setRecordState(false);
        		String err = "";
        		switch (what) {
        		case MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN:
        			err = "MEDIA_RECORDER_INFO_UNKNOWN";
        			break;
        		case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
        			err = "MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED";
        			break;
        		case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
        			err = "MEDIA_RECORDER_INFO_MAX_DURATION_REACHED";
        			break;
        		default:
        			break;
        		}
        		Toast.makeText(MainActivity.this, "¼�Ƴ���:" + err, Toast.LENGTH_SHORT).show();
                 }
             });
	}
	
	class TestVideoListener implements OnClickListener { 
        @Override 
        public void onClick(View v) { 
            if (v == start) { 
            	startRecording();
            } 
            if (v == stop) { 
            	stopRecording();
            } 
        } 

    } 
	
	private void startRecording() {
    	setRecordState(true);
        mediaRecorderInit();
        try { 
            mediaRecorder.prepare();
            mediaRecorder.start();
            Toast.makeText(MainActivity.this, "��ʼ¼��", Toast.LENGTH_SHORT).show();
        } catch (IllegalStateException e) { 
            e.printStackTrace();
        } catch (Exception e) { 
            e.printStackTrace();
        } 
	}
	
	private void stopRecording() {
    	setRecordState(false);
        if (mediaRecorder != null) { 
            // ֹͣ¼�� 
            mediaRecorder.stop();
            // �ͷ���Դ 
            mediaRecorder.release();
            mediaRecorder = null;
            Toast.makeText(MainActivity.this, "ֹͣ¼�񣬲������ļ�", Toast.LENGTH_SHORT).show();
        } 
	}
}
